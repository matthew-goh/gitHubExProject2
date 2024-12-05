package models

import baseSpec.BaseSpec
import play.api.data._

class CreateRequestBodySpec extends BaseSpec {
  val createForm: Form[CreateRequestBody] = CreateRequestBody.createForm
  val formData: CreateRequestBody = CreateRequestBody("test.txt", "Test commit", "test content")
  val formDataInvalid: CreateRequestBody = CreateRequestBody("badfile", "", "")

  "Create file form" should {
    "bind" when {
      "with a valid response" in {
        val completedForm = createForm.bind(Map("fileName" -> "test.txt",
          "commitMessage" -> "Test commit",
          "fileContent" -> "test content"))

        completedForm.value shouldBe Some(formData)
        completedForm.errors shouldBe List.empty
        completedForm.data shouldBe Map("fileName" -> "test.txt",
          "commitMessage" -> "Test commit",
          "fileContent" -> "test content")
      }

      "with a invalid response (invalid file name and empty commit message)" in {
        val completedForm = createForm.bind(Map("fileName" -> "badfile",
          "commitMessage" -> "",
          "fileContent" -> ""))

        completedForm.value shouldBe None
        completedForm.errors shouldBe List(FormError("fileName", List("Invalid file name"), List()),
          FormError("commitMessage", List("error.required"), List()))
      }

      "with no response" in {
        val completedForm = createForm.bind(Map.empty[String, String])
        completedForm.value shouldBe None
        completedForm.errors shouldBe List(FormError("fileName", List("error.required"), List()),
          FormError("commitMessage", List("error.required"), List()),
          FormError("fileContent", List("error.required"), List()))
      }
    }

    "fill" when {
      "with a valid response" in {
        val filledForm = createForm.fill(formData)
        filledForm.value shouldBe Some(formData)
        filledForm.errors shouldBe List.empty
        filledForm.data shouldBe Map("fileName" -> "test.txt",
          "commitMessage" -> "Test commit",
          "fileContent" -> "test content")
      }

      "with an invalid response (unsubmitted)" in {
        val filledForm = createForm.fill(formDataInvalid)
        filledForm.value shouldBe Some(formDataInvalid)
        filledForm.errors shouldBe List.empty
        filledForm.data shouldBe Map("fileName" -> "badfile",
          "commitMessage" -> "",
          "fileContent" -> "")
      }
    }
  }
}
