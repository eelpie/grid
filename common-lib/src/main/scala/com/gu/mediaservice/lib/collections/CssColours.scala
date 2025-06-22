package com.gu.mediaservice.lib.collections

trait CssColours {

  // These use Source swatches
  private val collectionColours = Map(
    "hone/biz & cash" -> "#c98a07",
    "home/home news" -> "#022164",
    "home/international" -> "#022164",
    "home/ofm" -> "#f2327d",
    "home/magazine/" -> "#5b1e4a",
    "home/sensemakers" -> "#3c9bf9",
    "home/sport" -> "#00663b",
    "home/supplements" -> "#008083",
    "home" -> "#052962"
  )

  def getCssColour(path: List[String]): Option[String] = {
    def forPath(depth: Int, default: Option[String]): Option[String] = {
      if (depth > path.size) {
        default
      } else {
        val pathId = path.take(depth).mkString("/")
        collectionColours.get(pathId).map { colour =>
          forPath(depth + 1, Some(colour))
        }.getOrElse {
          default
        }
      }
    }
    // recurse drop the path return the furthest leaf node
    forPath(depth = 1, default = None)
  }

}
