package models

import baseSpec.BaseSpec
import play.api.data._

class DeleteRequestBodySpec extends BaseSpec {
  val deleteForm: Form[DeleteRequestBody] = DeleteRequestBody.deleteForm
  lazy val formData: DeleteRequestBody = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")
  lazy val formDataInvalid: DeleteRequestBody = DeleteRequestBody("", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")

  "Delete file form" should {
    "bind" when {
      "with a valid response" in {
        val completedForm = deleteForm.bind(Map("commitMessage" -> "Test delete",
          "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7"))

        completedForm.value shouldBe Some(formData)
        completedForm.errors shouldBe List.empty
        completedForm.data shouldBe Map("commitMessage" -> "Test delete",
          "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7")
      }

      "with a invalid response (empty commit message)" in {
        val completedForm = deleteForm.bind(Map("commitMessage" -> "",
          "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7"))

        completedForm.value shouldBe None
        completedForm.errors shouldBe List(FormError("commitMessage", List("error.required"), List()))
      }

      "with no response" in {
        val completedForm = deleteForm.bind(Map.empty[String, String])
        completedForm.value shouldBe None
        completedForm.errors shouldBe List(FormError("commitMessage", List("error.required"), List()),
          FormError("fileSHA", List("error.required"), List()))
      }
    }

    "fill" when {
      "with a valid response" in {
        val filledForm = deleteForm.fill(formData)
        filledForm.value shouldBe Some(formData)
        filledForm.errors shouldBe List.empty
        filledForm.data shouldBe Map("commitMessage" -> "Test delete",
          "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7")
      }

      "with an invalid response (unsubmitted)" in {
        val filledForm = deleteForm.fill(formDataInvalid)
        filledForm.value shouldBe Some(formDataInvalid)
        filledForm.errors shouldBe List.empty
        filledForm.data shouldBe Map("commitMessage" -> "",
          "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7")
      }
    }
  }
}
