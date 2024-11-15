package models

import play.api.libs.json.{Json, OFormat}

case class GithubRepo(id: Int, name: String)

object GithubRepo {
  implicit val formats: OFormat[GithubRepo] = Json.format[GithubRepo]
}