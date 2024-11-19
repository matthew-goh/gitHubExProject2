package models

import play.api.libs.json.{Json, OFormat}

case class FileInfo(name: String, path: String, sha: String, content: String)

object FileInfo {
  implicit val formats: OFormat[FileInfo] = Json.format[FileInfo]
}
