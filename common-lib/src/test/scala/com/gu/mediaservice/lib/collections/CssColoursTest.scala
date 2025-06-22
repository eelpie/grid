package com.gu.mediaservice.lib.collections

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CssColoursTest extends AnyFunSpec with Matchers with CssColours {

  describe("CssColours") {
    describe("getCssColour") {
      it("should return exact match for specific collections") {
        getCssColour(List("home", "supplements")) shouldBe Some("#008083")
      }
      it("should default of none for collections with no specific colour preferences or parents with a colour") {
        getCssColour(List("unknown")) shouldBe None
      }
      it("should return colour of parent for collection with no specific colour") {
        getCssColour(List("home", "something")) shouldBe Some("#052962")
      }
      it("should return colour of closet parent for collection with no specific colour") {
        getCssColour(List("home", "supplements", "something", "somethingelse")) shouldBe Some("#008083")
      }
    }
  }

}
