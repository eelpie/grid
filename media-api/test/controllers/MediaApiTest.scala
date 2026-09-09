package controllers

import lib.querysyntax.Parser
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MediaApiTest extends AnyFunSpec with Matchers {

  describe("MediaApi.deletedImageUploaderScope") {
    val identity = "user@example.test"

    it("scopes an explicit deleted search for users without global delete permission") {
      MediaApi.deletedImageUploaderScope(
        Parser.run("cats is:deleted"),
        hasGlobalDeletePermission = false,
        identity = identity
      ) shouldBe Some(identity)
    }

    it("does not scope literal text or an explicit deleted exclusion") {
      MediaApi.deletedImageUploaderScope(
        Parser.run("cats \"is:deleted\""),
        hasGlobalDeletePermission = false,
        identity = identity
      ) shouldBe None
      MediaApi.deletedImageUploaderScope(
        Parser.run("cats -is:deleted"),
        hasGlobalDeletePermission = false,
        identity = identity
      ) shouldBe None
    }

    it("does not scope users with global delete permission") {
      MediaApi.deletedImageUploaderScope(
        Parser.run("cats is:deleted"),
        hasGlobalDeletePermission = true,
        identity = identity
      ) shouldBe None
    }
  }
}
