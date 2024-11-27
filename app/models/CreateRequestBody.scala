package models

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

case class CreateRequestBody(fileName: String, commitMessage: String, fileContent: String)

object CreateRequestBody {
  implicit val formats: OFormat[CreateRequestBody] = Json.format[CreateRequestBody]

  val fileNamePattern = "^([\\w\\s-]+/)*[\\w\\s-]+\\.[A-Za-z]{2,4}$".r

  val createForm: Form[CreateRequestBody] = Form(
    mapping(
      "fileName" -> nonEmptyText
        .verifying(Validation.regexConstraint(fileNamePattern, "Invalid file name")),
      "commitMessage" -> nonEmptyText,
      "fileContent" -> text
    )(CreateRequestBody.apply)(CreateRequestBody.unapply)
  )
}