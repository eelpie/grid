package com.gu.mediaservice.lib.imaging.im4jwrapper

import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.model.{Bounds, Dimensions}
import org.im4java.core.IMOperation

import java.io.File
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext


object ImageMagick extends GridLogging {
  implicit val ctx: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Config.imagingThreadPoolSize))

  def addImage(source: File): IMOperation = {
    val op = new IMOperation
    op.addImage(source.getAbsolutePath)
    op
  }
  def quality(op: IMOperation)(qual: Double): IMOperation = {
    op.quality(qual)
    op
  }
  def crop(op: IMOperation)(b: Bounds): IMOperation = {
    op.crop(b.width, b.height, b.x, b.y)
    op
  }
  def profile(op: IMOperation)(profileFileLocation: String): IMOperation = {
    op.profile(profileFileLocation)
    op
  }

  def rotate(op: IMOperation)(angle: Double): IMOperation = {
    op.rotate(angle)
    op
  }

  def thumbnail(op: IMOperation)(width: Int): IMOperation = {
    op.thumbnail(width)
    op
  }
  def resize(op: IMOperation)(maxSize: Int): IMOperation = {
    op.resize(maxSize, maxSize)
    op
  }
  def scale(op: IMOperation)(dimensions: Dimensions): IMOperation = {
    op.scale(dimensions.width, dimensions.height)
    op
  }
  def format(op: IMOperation)(definition: String): IMOperation = {
    op.format(definition)
    op
  }
  def flatten(op: IMOperation): IMOperation = {
    op.flatten()
    op
  }
}
