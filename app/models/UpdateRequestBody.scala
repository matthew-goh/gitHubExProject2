package models

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

case class UpdateRequestBody(commitMessage: String, newFileContent: String, fileSHA: String)

object UpdateRequestBody {
  implicit val formats: OFormat[UpdateRequestBody] = Json.format[UpdateRequestBody]

  val updateForm: Form[UpdateRequestBody] = Form(
    mapping(
      "commitMessage" -> nonEmptyText,
      "newFileContent" -> text,
      "fileSHA" -> text // will be readonly in the form
    )(UpdateRequestBody.apply)(UpdateRequestBody.unapply)
  )
}