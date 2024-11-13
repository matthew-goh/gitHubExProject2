package models

import play.api.libs.json.{Json, OFormat}

import java.time.Instant

// Your mongodb should contain users which will have the following information:
// username, date account created, location, number of followers, number following
case class User(login: String, location: Option[String], created_at: Instant, followers: Int, following: Int)

object User {
  implicit val formats: OFormat[User] = Json.format[User]
}
