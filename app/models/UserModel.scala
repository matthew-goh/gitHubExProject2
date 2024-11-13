package models

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.{JsString, Json, OFormat, Writes}

import java.time.Instant

// Your mongodb should contain users which will have the following information:
// username, date account created, location, number of followers, number following
case class UserModel(username: String, location: Option[String], accountCreatedTime: Instant, numFollowers: Int, numFollowing: Int)

object UserModel {
  // Implicit Writes for Instant
//  implicit val instantWrites: Writes[Instant] = Writes[Instant](instant => JsString(instant.toString))

  implicit val formats: OFormat[UserModel] = Json.format[UserModel]

//  val dataForm: Form[DataModel] = Form(
//    mapping(
//      "_id" -> nonEmptyText,
//      "name" -> nonEmptyText,
//      "description" -> text,
//      "pageCount" -> number(min = 0, max = 2000)
//    )(DataModel.apply)(DataModel.unapply)
//  )

//  val testDateTimeString = "2024-10-28T15:22:40Z"
//  val parsedDateTime: DateTime = DateTime.fromIsoDateTimeString(testDateTimeString).get
//  println(parsedDateTime.year)

//  val testInstant = Instant.parse(testDateTimeString)
//  println(testInstant)
}