package com.gu.mediaservice.lib.imaging

import com.gu.mediaservice.model.Asset

case class ExportResult(id: String, masterCrop: Asset, othersizings: List[Asset])
class UnsupportedCropOutputTypeException extends Exception
