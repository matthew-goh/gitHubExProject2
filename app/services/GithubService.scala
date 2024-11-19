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

  def createGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: CreateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val encodedContent = Base64.getEncoder.encodeToString(body.fileContent.getBytes("UTF-8"))
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "content" -> encodedContent
    )
    // println(requestBody)
    connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }

  def updateGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: UpdateRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val encodedContent = Base64.getEncoder.encodeToString(body.newFileContent.getBytes("UTF-8"))
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "content" -> encodedContent,
      "sha" -> body.fileSHA
    )
    // println(requestBody)
    connector.createUpdate(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }

  def deleteGithubFile(urlOverride: Option[String] = None, username: String, repoName: String, path: String, body: DeleteRequestBody)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val requestBody = Json.obj(
      "message" -> body.commitMessage,
      "sha" -> body.fileSHA
    )
     println(requestBody)
    connector.delete(urlOverride.getOrElse(s"https://api.github.com/repos/$username/$repoName/contents/$path"), requestBody)
  }
}
