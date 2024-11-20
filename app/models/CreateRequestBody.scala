package models

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

case class CreateRequestBody(commitMessage: String, fileContent: String)

object CreateRequestBody {
  implicit val formats: OFormat[CreateRequestBody] = Json.format[CreateRequestBody]

  val createForm: Form[CreateRequestBody] = Form(
    mapping(
      "commitMessage" -> nonEmptyText,
      "fileContent" -> text
    )(CreateRequestBody.apply)(CreateRequestBody.unapply)
  )
}