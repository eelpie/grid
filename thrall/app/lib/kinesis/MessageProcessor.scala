package lib.kinesis

import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.EsResponse
import com.gu.mediaservice.lib.elasticsearch.{ElasticNotFoundException, Running}
import com.gu.mediaservice.lib.events.UsageEvents
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, combineMarkers}
import com.gu.mediaservice.model.{AddImageLeaseMessage, CreateMigrationIndexMessage, DeleteImageExportsMessage, DeleteImageMessage, DeleteUsagesMessage, ImageMessage, MigrateImageMessage, RemoveImageLeaseMessage, ReplaceImageLeasesMessage, SetImageCollectionsMessage, SoftDeleteImageMessage, ThrallMessage, UnSoftDeleteImageMessage, UpdateImageExportsMessage, UpdateImagePhotoshootMetadataMessage, UpdateImageSyndicationMetadataMessage, UpdateImageUsagesMessage, UpdateImageUserMetadataMessage}
import com.gu.mediaservice.model.usage.{Usage, UsageNotice}
import instances.{InstanceMessageSender, InstanceStatusMessage}
// import all except `Right`, which otherwise shadows the type used in `Either`s
import com.gu.mediaservice.model.{Right => _, _}
import com.gu.mediaservice.syntax.MessageSubjects
import lib._
import lib.elasticsearch._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait MigrationFailure

case class ProjectionFailure(message: String) extends Exception(message) with MigrationFailure
case class GetVersionFailure(message: String) extends Exception(message) with MigrationFailure
case class VersionComparisonFailure(message: String) extends Exception(message) with MigrationFailure
case class InsertImageFailure(message: String) extends Exception(message) with MigrationFailure

class MessageProcessor(
  es: ElasticSearch,
  store: ThrallStore,
  metadataEditorNotifications: MetadataEditorNotifications,
  gridClient: GridClient,
  auth: Authentication,
  instanceMessageSender: InstanceMessageSender,
  usageEvents: UsageEvents
) extends GridLogging with MessageSubjects {

  def process(updateMessage: ThrallMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Any] = {
    updateMessage match {
      case message: ImageMessage => indexImage(message, logMarker)
      case message: DeleteImageMessage => deleteImage(message, logMarker)
      case message: SoftDeleteImageMessage => softDeleteImage(message, logMarker)
      case message: UnSoftDeleteImageMessage => unSoftDeleteImage(message, logMarker)
      case message: DeleteImageExportsMessage => deleteImageExports(message, logMarker)
      case message: UpdateImageExportsMessage => updateImageExports(message, logMarker)
      case message: UpdateImageUserMetadataMessage => updateImageUserMetadata(message, logMarker)
      case message: UpdateImageUsagesMessage => updateImageUsages(message, logMarker)
      case message: ReplaceImageLeasesMessage => replaceImageLeases(message, logMarker)
      case message: AddImageLeaseMessage => addImageLease(message, logMarker)
      case message: RemoveImageLeaseMessage => removeImageLease(message, logMarker)
      case message: SetImageCollectionsMessage => setImageCollections(message, logMarker)
      case message: DeleteUsagesMessage => deleteAllUsages(message, logMarker)
      case message: DeleteSingleUsageMessage => deleteSingleUsage(message, logMarker)
      case message: UpdateImageSyndicationMetadataMessage => upsertSyndicationRightsOnly(message, logMarker)
      case message: UpdateImagePhotoshootMetadataMessage => updateImagePhotoshoot(message, logMarker)
      case message: CreateMigrationIndexMessage => createMigrationIndex(message, logMarker)
      case message: MigrateImageMessage => migrateImage(message, logMarker)
      case message: UpsertFromProjectionMessage => upsertImageFromProjection(message, logMarker)
      case message: UpdateUsageStatusMessage => updateUsageStatus(message, logMarker)
      case message: CompleteMigrationMessage => completeMigration(message, logMarker)
      case message: CreateInstanceMessage => setupNewInstance(message, logMarker)
      case _ =>
        logger.info(s"Unmatched ThrallMessage type: ${updateMessage.subject}; ignoring")
        Future.successful(())
    }
  }

  def updateImageUsages(message: UpdateImageUsagesMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[List[ElasticSearchUpdateResponse]] = {
    implicit val iw: OWrites[Instance] = Json.writes[Instance]
    implicit val unw: OWrites[UsageNotice] = Json.writes[UsageNotice]
    implicit val lm: LogMarker = combineMarkers(message, logMarker)
    val usages = message.usageNotice.usageJson.as[Seq[Usage]]
    implicit val instance: Instance = message.instance
    Future.traverse(es.updateImageUsages(message.id, usages, message.lastModified))(_.recoverWith {
      case ElasticNotFoundException => Future.successful(ElasticSearchUpdateResponse())
    })
  }

  private def indexImage(message: ImageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    es.migrationAwareIndexImage(message.id, message.image, message.lastModified)(ec, logMarker, message.instance)

  private def upsertImageFromProjection(message: UpsertFromProjectionMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    implicit val implicitLogMarker: LogMarker = logMarker ++ Map("imageId" -> message.id)

    // do not write into migration index, even if migration is running; let the standard
    // images-for-migration process find and migrate it. even if it has previously
    // been migrated, this directInsert will wipe out the esInfo marker, requeueing this image
    // for migration.
    es.directInsert(message.image, es.imagesCurrentAlias(message.instance))
      .recover {
        case t: Throwable =>
          logger.error(logMarker, s"Failed to directly upsert image ${message.image.id} from projection", t)
          Future.successful(())
      }
  }

  private def migrateImage(message: MigrateImageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    implicit val implicitLogMarker: LogMarker = logMarker ++ Map("imageId" -> message.id)
    implicit val instance: Instance = message.instance
    val maybeStart = message.maybeImageWithVersion match {
      case Left(errorMessage) =>
        Future.failed(ProjectionFailure(errorMessage))
      case Right((image, expectedVersion)) => Future.successful((image, expectedVersion))
    }
    maybeStart.flatMap {
      case (image, expectedVersion) => es.getImageVersion(message.id).transformWith {
        case Success(Some(currentVersion)) => Future.successful((image, expectedVersion, currentVersion))
        case Success(None) => Future.failed(GetVersionFailure(s"No version found for image id: ${image.id}"))
        case Failure(exception) => Future.failed(GetVersionFailure(exception.toString))
      }
    }.flatMap {
      case (image, expectedVersion, currentVersion) => if (expectedVersion == currentVersion) {
        Future.successful(image)
      } else {
        Future.failed(VersionComparisonFailure(s"Version comparison failed for image id: ${image.id} -> current = $currentVersion, expected = $expectedVersion"))
      }
    }.flatMap(
      image => es.directInsert(image, es.imagesMigrationAlias(instance)).transform {
        case s@Success(_) => s
        case Failure(exception) => Failure(InsertImageFailure(exception.toString))
      }
    ).flatMap { insertResult =>
      logger.info(logMarker, s"Successfully migrated image with id: ${message.id}, setting 'migratedTo' on current index")
      es.setMigrationInfo(imageId = message.id, migrationInfo = MigrationInfo(migratedTo = Some(insertResult.indexName)))
    }.recoverWith {
      case versionComparisonFailure: VersionComparisonFailure =>
        logger.error(logMarker, s"Postponed migration of image with id: ${message.id}: cause: ${versionComparisonFailure.getMessage}, this will get picked up shortly")
        Future.successful(())
      case failure: MigrationFailure =>
        logger.error(logMarker, s"Failed to migrate image with id: ${message.id}: cause: ${failure.getMessage}, attaching failure to document in current index")
        val migrationIndexName = es.migrationStatus() match {
          case running: Running => running.migrationIndexName
          case _ => "Unknown migration index name"
        }
        es.setMigrationInfo(imageId = message.id, migrationInfo = MigrationInfo(failures = Some(Map(migrationIndexName -> failure.getMessage))))
    }
  }

  private def updateImageExports(message: UpdateImageExportsMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    Future.sequence(
      es.updateImageExports(message.id, message.crops, message.lastModified)(ec, logMarker, message.instance))
  }

  private def deleteImageExports(message: DeleteImageExportsMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    Future.sequence(
      es.deleteImageExports(message.id, message.lastModified)(ec, logMarker, message.instance))
  }

  private def softDeleteImage(message: SoftDeleteImageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.applySoftDelete(message.id, message.softDeletedMetadata, message.lastModified)(ec, logMarker, message.instance)).map { r =>
      usageEvents.softDelete(instance = message.instance, image = message.id)
      r
    }

  private def unSoftDeleteImage(message: UnSoftDeleteImageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.applyUnSoftDelete(message.id, message.lastModified)(ec, logMarker, message.instance)).map { r =>
      usageEvents.unsoftDelete(instance = message.instance, image = message.id)
      r
    }

  private def updateImageUserMetadata(message: UpdateImageUserMetadataMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.applyImageMetadataOverride(message.id, message.edits, message.lastModified)(ec, logMarker, message.instance))

  private def replaceImageLeases(message: ReplaceImageLeasesMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.replaceImageLeases(message.id, message.leases, message.lastModified)(ec, logMarker, message.instance))

  private def addImageLease(message: AddImageLeaseMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.addImageLease(message.id, message.lease, message.lastModified)(ec, logMarker, message.instance))

  private def removeImageLease(message: RemoveImageLeaseMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[List[ElasticSearchUpdateResponse]] =
    Future.sequence(es.removeImageLease(message.id, Some(message.leaseId), message.lastModified)(ec, logMarker, message.instance))

  private def setImageCollections(message: SetImageCollectionsMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.setImageCollections(message.id, message.collections, message.lastModified)(ec, logMarker, message.instance))

  private def deleteImage(message: DeleteImageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    Future.sequence({
      implicit val marker: LogMarker = logMarker ++ imageIdMarker(ImageId(message.id))
      implicit val instance: Instance = message.instance
      // if we cannot delete the image as it's "protected", succeed and delete
      // the message anyway.
      logger.info(marker, "ES6 Deleting image: " + message.id)
      es.deleteImage(message.id).map { requests =>
        requests.map {
          _: ElasticSearchDeleteResponse =>
            store.deleteOriginal(message.id, message.instance)
            store.deleteThumbnail(message.id, message.instance)
            store.deletePNG(message.id, message.instance)
            metadataEditorNotifications.publishImageDeletion(message.id, message.instance)
            usageEvents.deleteImage(instance = message.instance, image = message.id)
            EsResponse(s"Image deleted: ${message.id}")
        } recoverWith {
          case ImageNotDeletable =>
            logger.info(marker, "Could not delete image")
            Future.successful(EsResponse(s"Image cannot be deleted: ${message.id}"))
        }
      }
    })


  }

  private def deleteAllUsages(message: DeleteUsagesMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) =
    Future.sequence(es.deleteAllImageUsages(message.id, message.lastModified)(ec, logMarker, message.instance))

  private def deleteSingleUsage(message: DeleteSingleUsageMessage, logMarker: LogMarker)(implicit ec: ExecutionContext) = {
    Future.sequence(es.deleteSingleImageUsage(message.id, message.usageId, message.lastModified)(ec, logMarker, message.instance))
  }

  private def updateUsageStatus(message: UpdateUsageStatusMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[List[ElasticSearchUpdateResponse]] = {
      implicit val lm: LogMarker = combineMarkers(message, logMarker)
      val usage = message.usageNotice.usageJson.as[Seq[Usage]]
      implicit val instance: Instance = message.instance
      Future.traverse(es.updateUsageStatus(message.id, usage, message.lastModified))(_.recoverWith {
        case ElasticNotFoundException => Future.successful(ElasticSearchUpdateResponse())
    })
  }

  def upsertSyndicationRightsOnly(message: UpdateImageSyndicationMetadataMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Any] = {
    implicit val marker: LogMarker = logMarker ++ imageIdMarker(ImageId(message.id))
    implicit val instance: Instance = message.instance
    es.getImage(message.id) map {
      case Some(image) =>
        val photoshoot = image.userMetadata.flatMap(_.photoshoot)
        logger.info(marker, s"Upserting syndication rights for image ${message.id} in photoshoot $photoshoot with rights ${Json.toJson(message.maybeSyndicationRights)}")
        es.updateImageSyndicationRights(message.id, message.maybeSyndicationRights, message.lastModified)
      case _ => logger.info(marker, s"Image ${message.id} not found")
    }
  }

  def updateImagePhotoshoot(message: UpdateImagePhotoshootMetadataMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val marker: LogMarker = logMarker ++ imageIdMarker(ImageId(message.id))
    implicit val instance: Instance = message.instance
    for {
      imageOpt <- es.getImage(message.id)
      prevPhotoshootOpt = imageOpt.flatMap(_.userMetadata.flatMap(_.photoshoot))
      _ <- updateImageUserMetadata(UpdateImageUserMetadataMessage(message.id, message.lastModified, message.edits, message.instance), logMarker)
    } yield logger.info(marker, s"Moved image ${message.id} from $prevPhotoshootOpt to ${message.edits.photoshoot}")
  }

  def createMigrationIndex(message: CreateMigrationIndexMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      es.startMigration(message.newIndexName)(logMarker, message.instance)
    }
  }

  def completeMigration(message: CompleteMigrationMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val instance: Instance = message.instance
    es.completeMigration(logMarker)
  }

  private def setupNewInstance(message: CreateInstanceMessage, logMarker: LogMarker)(implicit ec: ExecutionContext): Future[Unit] = {
    val instance = message.instance
    logger.info("Ensuring indexes for create instance message: " + instance)
    Future.successful {
      es.ensureIndexExistsAndAliasAssigned(alias = es.imagesCurrentAlias(instance), index = instance.id + "_index")
    }.flatMap { _ =>
      logger.info("Creating Home collection for new instance: " + instance)
      implicit val i: Instance = instance
      gridClient.createCollection("Home", auth.innerServiceCall).map { r: Option[Collection] =>
        logger.info("Created collection for new instance: " + instance)
        // Notify instances service that this instance can be marked as ready to use
        logger.info(s"Sending instance ready message for ${instance.id}")
        instanceMessageSender.send(InstanceStatusMessage(instance = instance.id, status = "ready"))
      }
    }
  }

}
