import play.sbt.PlayImport.PlayKeys._

import scala.sys.process._

val commonSettings = Seq(
  scalaVersion := "2.12.8",
  description := "grid",
  organization := "com.gu",
  dockerUsername := Some("eelpie"),
  dockerUpdateLatest := true,
  version := "0.1",
  scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-Xfatal-warnings"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.5"
  )
)

lazy val root = project("grid", path = Some("."))
  .aggregate(commonLib, auth, collections, cropper, imageLoader, leases, thrall, kahuna, metadataEditor, usage, mediaApi)

addCommandAlias("runAll", "all auth/run media-api/run thrall/run image-loader/run metadata-editor/run kahuna/run collections/run cropper/run usage/run leases/run")

// Required to allow us to run more than four play projects in parallel from a single SBT shell
Global / concurrentRestrictions := Seq(
  Tags.limit(Tags.CPU, Math.min(1, java.lang.Runtime.getRuntime.availableProcessors - 1)),
  Tags.limit(Tags.Test, 1),
  Tags.limitAll(12)
)

val awsSdkVersion = "1.11.302"
val elastic4sVersion = "6.4.0"

lazy val commonLib = project("common-lib").settings(
  libraryDependencies ++= Seq(
    // also exists in plugins.sbt, TODO deduplicate this
    "com.typesafe.play" %% "play" % "2.6.20", ws,
    "com.typesafe.play" %% "play-json-joda" % "2.6.9",
    "com.typesafe.play" %% "filters-helpers" % "2.6.20",
    "com.gu" %% "pan-domain-auth-play_2-6" % "0.8.0",
    "com.gu" %% "editorial-permissions-client" % "2.0",
    "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-cloudfront" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-kinesis" % awsSdkVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
    "com.gu" %% "box" % "0.2.0",
    "com.gu" %% "thrift-serializer" % "3.0.0",
    "org.scalaz.stream" %% "scalaz-stream" % "0.8.6",
    "com.drewnoakes" % "metadata-extractor" % "2.11.0",
    "org.im4java" % "im4java" % "1.4.0",
    "com.gu" % "kinesis-logback-appender" % "1.4.2",
    "net.logstash.logback" % "logstash-logback-encoder" % "5.0",
    "com.typesafe.play" %% "play-logback" % "2.6.15", // needed when running the scripts
    "org.scalacheck" %% "scalacheck" % "1.14.0",
    // needed to parse conditional statements in `logback.xml`
    // i.e. to only log to disk in DEV
    // see: https://logback.qos.ch/setup.html#janino
    "org.codehaus.janino" % "janino" % "3.0.6"
  ),
  dependencyOverrides += "org.apache.thrift" % "libthrift" % "0.9.1"
)

lazy val auth = playProject("auth", 9011)

lazy val collections = playProject("collections", 9010)

lazy val cropper = playProject("cropper", 9006).settings {
  import com.typesafe.sbt.packager.docker._
  Seq(
    dockerBaseImage := "openjdk:11-jre-stretch", // addresses ca-cert issues with vanilla Debian and JDK 11
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "apt-get", "update"),
      ExecCmd("RUN", "apt-get", "install", "-y", "apt-utils"),
      ExecCmd("RUN", "apt-get", "upgrade", "-y"),
      ExecCmd("RUN", "apt-get", "install", "-y", "graphicsmagick"),
      ExecCmd("RUN", "apt-get", "install", "-y", "graphicsmagick-imagemagick-compat"),
      ExecCmd("RUN", "apt-get", "install", "-y", "pngquant"),
      ExecCmd("RUN", "apt-get", "install", "-y", "libimage-exiftool-perl")
    )
  )
}

lazy val imageLoader = playProject("image-loader", 9003).settings {
  import com.typesafe.sbt.packager.docker._
  Seq(
    libraryDependencies ++= Seq("com.squareup.okhttp3" % "okhttp" % "3.12.1"),
    dockerBaseImage := "openjdk:11-jre-stretch", // addresses ca-cert issues with vanilla Debian and JDK 11
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "apt-get", "update"),
      ExecCmd("RUN", "apt-get", "install", "-y", "apt-utils"),
      ExecCmd("RUN", "apt-get", "upgrade", "-y"),
      ExecCmd("RUN", "apt-get", "install", "-y", "graphicsmagick"),
      ExecCmd("RUN", "apt-get", "install", "-y", "graphicsmagick-imagemagick-compat"),
      ExecCmd("RUN", "apt-get", "install", "-y", "pngquant"),
      ExecCmd("RUN", "apt-get", "install", "-y", "libimage-exiftool-perl")
    )
  )
}

lazy val kahuna = playProject("kahuna", 9005)

lazy val leases = playProject("leases", 9012).settings(
  libraryDependencies ++= Seq(
    "com.gu" %% "scanamo" % "1.0.0-M8"
  )
)

lazy val mediaApi = playProject("media-api", 9001).settings(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-email" % "1.5",
    "org.parboiled" %% "parboiled" % "2.1.5",
    "org.http4s" %% "http4s-core" % "0.18.7",
    "org.mockito" % "mockito-core" % "2.18.0"
  )
).settings(testSettings)

lazy val metadataEditor = playProject("metadata-editor", 9007)

lazy val thrall = playProject("thrall", 9002).settings(
  libraryDependencies ++= Seq(
    "org.codehaus.groovy" % "groovy-json" % "2.4.4",
    "com.yakaz.elasticsearch.plugins" % "elasticsearch-action-updatebyquery" % "2.2.0",
    "com.amazonaws" % "amazon-kinesis-client" % "1.8.10"
  )
).settings(testSettings)

lazy val usage = playProject("usage", 9009).settings(
  libraryDependencies ++= Seq(
    "com.gu" %% "content-api-client" % "11.53",
    "io.reactivex" %% "rxscala" % "0.26.5",
    "com.amazonaws" % "amazon-kinesis-client" % "1.8.10"
  )
)

lazy val scripts = project("scripts")
  .dependsOn(commonLib)

lazy val migration = project("migration")
  .dependsOn(commonLib).
  settings(commonSettings,
    mainClass in Compile := Some("Main"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case _ => MergeStrategy.first
    })

def project(projectName: String, path: Option[String] = None): Project =
  Project(projectName, file(path.getOrElse(projectName)))
    .settings(commonSettings)

def playProject(projectName: String, port: Int): Project = {
  project(projectName, None)
    .enablePlugins(PlayScala, BuildInfoPlugin, DockerPlugin)
    .dependsOn(commonLib)
    .settings(commonSettings ++ Seq(
      playDefaultPort := port,
      dockerBaseImage := "openjdk:11-jre",
      dockerExposedPorts in Docker := Seq(port),
    ))
}

val testSettings = Seq(
  testOptions in Test += Tests.Setup(_ => {
    println(s"Launching docker container with ES")
    s"docker-compose --file docker-compose-test.yml --project-name grid-test up -d".!

    // This is needed to ensure docker has had enough time to start up
    Thread.sleep(30000)
  }),
  testOptions in Test += Tests.Cleanup(_ => {
    println(s"Removing container")
    s"docker-compose --file docker-compose-test.yml --project-name grid-test down".!
  })
)

