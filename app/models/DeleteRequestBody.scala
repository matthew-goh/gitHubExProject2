package models

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

case class DeleteRequestBody(commitMessage: String, fileSHA: String)

object DeleteRequestBody {
  implicit val formats: OFormat[DeleteRequestBody] = Json.format[DeleteRequestBody]

  val deleteForm: Form[DeleteRequestBody] = Form(
    mapping(
      "commitMessage" -> nonEmptyText,
      "fileSHA" -> text // will be readonly in the form
    )(DeleteRequestBody.apply)(DeleteRequestBody.unapply)
  )
}