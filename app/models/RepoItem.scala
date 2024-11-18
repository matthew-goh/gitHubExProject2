package models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Json, OFormat, OWrites, Reads}

case class RepoItem(name: String, path: String, itemType: String)

object RepoItem {
  // Custom Reads: Maps the "type" key in a JsValue to the "itemType" field in the case class
  implicit val reads: Reads[RepoItem] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "path").read[String] and
      (JsPath \ "type").read[String]
    )(RepoItem.apply _)

  implicit val writes: OWrites[RepoItem] = Json.writes[RepoItem]

  implicit val formats: OFormat[RepoItem] = OFormat(reads, writes)
}
