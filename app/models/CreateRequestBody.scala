package models

import play.api.libs.json.{Json, OFormat}

case class CreateRequestBody(commitMessage: String, fileContent: String)

object CreateRequestBody {
  implicit val formats: OFormat[CreateRequestBody] = Json.format[CreateRequestBody]
}