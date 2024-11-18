package connectors

import cats.data.EitherT
import models.APIError
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class GithubConnector @Inject()(ws: WSClient) {
  def get[Response](url: String)(implicit rds: OFormat[Response], ec: ExecutionContext): EitherT[Future, APIError, Response] = {
    val githubToken = sys.env.get("GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = githubToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token")
      case None => request
    }
    val response = requestWithAuth.get()

    // EitherT allows us to return either Future[APIError] or Future[Response]
    EitherT {
      response.map {
        result => {
          val resultJson: JsValue = result.json
          val message: Option[String] = (resultJson \ "message").asOpt[String]
          message match {
            case None => Right(resultJson.as[Response])
            case Some(_) => Left(APIError.BadAPIResponse(404, "Not found")) // message is "Not Found"
          }
        }
      }
      .recover { //case _: WSResponse =>
        case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
      }
    }
  }

  def getList[Response](url: String)(implicit rds: OFormat[Response], ec: ExecutionContext): EitherT[Future, APIError, Seq[Response]] = {
    val githubToken = sys.env.get("GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = githubToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token")
      case None => request
    }
    val response = requestWithAuth.get()

    // EitherT allows us to return either Future[APIError] or Future[Response]
    EitherT {
      response.map {
          result => {
            val resultJson: JsValue = result.json
            val message: Option[String] = (resultJson \ "message").asOpt[String]
            message match {
              case None => Right(resultJson.as[Seq[Response]])
              case Some(_) => Left(APIError.BadAPIResponse(404, "Not found")) // message is "Not Found"
            }
          }
        }
        .recover { //case _: WSResponse =>
          case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
        }
    }
  }
}

//{
//  "login": "matthew-goh",
//  "id": 186605436,
//  "node_id": "U_kgDOCx9ffA",
//  "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
//  "gravatar_id": "",
//  "url": "https://api.github.com/users/matthew-goh",
//  "html_url": "https://github.com/matthew-goh",
//  "followers_url": "https://api.github.com/users/matthew-goh/followers",
//  "following_url": "https://api.github.com/users/matthew-goh/following{/other_user}",
//  "gists_url": "https://api.github.com/users/matthew-goh/gists{/gist_id}",
//  "starred_url": "https://api.github.com/users/matthew-goh/starred{/owner}{/repo}",
//  "subscriptions_url": "https://api.github.com/users/matthew-goh/subscriptions",
//  "organizations_url": "https://api.github.com/users/matthew-goh/orgs",
//  "repos_url": "https://api.github.com/users/matthew-goh/repos",
//  "events_url": "https://api.github.com/users/matthew-goh/events{/privacy}",
//  "received_events_url": "https://api.github.com/users/matthew-goh/received_events",
//  "type": "User",
//  "user_view_type": "public",
//  "site_admin": false,
//  "name": "Matthew Goh",
//  "company": null,
//  "blog": "",
//  "location": null,
//  "email": null,
//  "hireable": null,
//  "bio": null,
//  "twitter_username": null,
//  "public_repos": 5,
//  "public_gists": 0,
//  "followers": 0,
//  "following": 0,
//  "created_at": "2024-10-28T15:22:40Z",
//  "updated_at": "2024-11-05T11:54:37Z"
//}