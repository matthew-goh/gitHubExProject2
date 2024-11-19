package models

import play.api.libs.json.{Json, OFormat}

case class UpdateRequestBody(commitMessage: String, newFileContent: String, fileSHA: String)

object UpdateRequestBody {
  implicit val formats: OFormat[UpdateRequestBody] = Json.format[UpdateRequestBody]
}