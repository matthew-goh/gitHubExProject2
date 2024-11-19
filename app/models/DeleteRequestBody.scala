package models

import play.api.libs.json.{Json, OFormat}

case class DeleteRequestBody(commitMessage: String, fileSHA: String)

object DeleteRequestBody {
  implicit val formats: OFormat[DeleteRequestBody] = Json.format[DeleteRequestBody]
}