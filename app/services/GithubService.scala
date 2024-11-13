package services

import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, User, UserModel}
import play.api.libs.json.JsValue

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class GithubService @Inject()(connector: GithubConnector) {

  def getGithubUser(urlOverride: Option[String] = None, username: String)(implicit ec: ExecutionContext): EitherT[Future, APIError, User] = {
    connector.get[User](urlOverride.getOrElse(s"https://api.github.com/users/$username"))
  }

  def convertToUserModel(user: User): UserModel = {
    val locationStr = user.location.getOrElse("")
    UserModel(username = user.login, location = locationStr, accountCreatedTime = user.created_at, numFollowers = user.followers, numFollowing = user.following)
  }
}
