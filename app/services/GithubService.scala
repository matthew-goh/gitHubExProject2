package services

import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, CreateRequestBody, DeleteRequestBody, FileInfo, GithubRepo, RepoItem, UpdateRequestBody, User, UserModel}
import play.api.libs.json._

import java.util.Base64
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

  def getGithubRepos(urlOverride: Option[String] = None, username: String)(implicit ec: ExecutionContext): EitherT[Future, APIError, Seq[GithubRepo]] = {
    connector.getList[GithubRepo](urlOverride.getOrElse(s"https://api.github.com/users/$username/repos"))
  }

  def getRepoItems(urlOverride: Option[String] = None, username: String, repoName: String, path: String = "")(implicit ec: ExecutionContext): EitherT[Future, APIError, Seq[RepoItem]] = {
    connector.getList[RepoItem](urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"))
  }

  def getFileInfo(urlOverride: Option[String] = None, username: String, repoName: String, path: String)(implicit ec: ExecutionContext): EitherT[Future, APIError, FileInfo] = {
    connector.get[FileInfo](urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"))
  }

  // path passed into these methods is the entire path to file, including file name
  def createGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: CreateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val encodedContent = Base64.getEncoder.encodeToString(body.fileContent.getBytes("UTF-8"))
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "content" -> encodedContent
    )
    connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }

  def updateGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: UpdateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val encodedContent = Base64.getEncoder.encodeToString(body.newFileContent.getBytes("UTF-8"))
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "content" -> encodedContent,
      "sha" -> body.fileSHA
    )
    connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }

  def deleteGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: DeleteRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "sha" -> body.fileSHA
    )
    connector.delete(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }

  ///// USING TYPE CLASSES /////
  trait ValidRequest[T] {
    def callGithubConnector(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: T)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue]
  }

  object ValidRequest {
    implicit object CreateRequest extends ValidRequest[CreateRequestBody] {
      def callGithubConnector(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: CreateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
        val encodedContent = Base64.getEncoder.encodeToString(body.fileContent.getBytes("UTF-8"))
        val requestBody = Json.obj(
          "message" -> body.commitMessage,
          "content" -> encodedContent
        )
        connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
      }
    }
    implicit object UpdateRequest extends ValidRequest[UpdateRequestBody] {
      def callGithubConnector(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: UpdateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
        val encodedContent = Base64.getEncoder.encodeToString(body.newFileContent.getBytes("UTF-8"))
        val requestBody = Json.obj(
          "message" -> body.commitMessage,
          "content" -> encodedContent,
          "sha" -> body.fileSHA
        )
        connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
      }
    }
    implicit object DeleteRequest extends ValidRequest[DeleteRequestBody] {
      def callGithubConnector(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: DeleteRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
        val requestBody = Json.obj(
          "message" -> body.commitMessage,
          "sha" -> body.fileSHA
        )
        connector.delete(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
      }
    }
  }

  def processRequestFromForm[T](urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: T)
                               (implicit ec: ExecutionContext, requestObj: ValidRequest[T]): EitherT[Future, APIError, JsValue] = {
    requestObj.callGithubConnector(urlOverride, username, repoName, path, body)
  }
  ///// END: USING TYPE CLASSES /////
}
