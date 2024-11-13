package services

import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, UserModel}
import play.api.libs.json.JsValue

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class GithubService @Inject()(connector: GithubConnector) {

  def getGithubUser(urlOverride: Option[String] = None, username: String)(implicit ec: ExecutionContext): EitherT[Future, APIError, UserModel] = {
    connector.get[UserModel](urlOverride.getOrElse(s"https://api.github.com/users/$username"))
  }

}
