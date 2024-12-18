package connectors

import cats.data.EitherT
import models.APIError
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class GithubConnector @Inject()(ws: WSClient) {
  def get[Response](url: String)(implicit rds: OFormat[Response], ec: ExecutionContext): EitherT[Future, APIError, Response] = {
    // val githubToken: Option[String] = sys.env.get("GITHUB_TOKEN")
    val personalToken: Option[String] = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request: WSRequest = ws.url(url)
    val requestWithAuth: WSRequest = personalToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token")
      case None => request
    }
    val response = requestWithAuth.get()

    // EitherT allows us to return either Future[APIError] or Future[Response]
    EitherT {
      response.map {
        result => {
          val resultJson: JsValue = Json.parse(result.body)
          val message: Option[String] = (resultJson \ "message").asOpt[String]
          resultJson.validate[Response] match {
            case JsSuccess(responseItem, _) => Right(responseItem)
            case JsError(_) => Left(APIError.BadAPIResponse(result.status, message.getOrElse("Unknown error")))
          }
        }
      }
      .recover { //case _: WSResponse =>
        case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
      }
    }
  }

  def getList[Response](url: String)(implicit rds: OFormat[Response], ec: ExecutionContext): EitherT[Future, APIError, Seq[Response]] = {
    // val githubToken: Option[String] = sys.env.get("GITHUB_TOKEN")
    val personalToken: Option[String] = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request: WSRequest = ws.url(url)
    val requestWithAuth: WSRequest = personalToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token")
      case None => request
    }
    val response = requestWithAuth.get()

    // EitherT allows us to return either Future[APIError] or Future[Response]
    EitherT {
      response.map {
          result => {
            val resultJson: JsValue = Json.parse(result.body)
            val message: Option[String] = (resultJson \ "message").asOpt[String]
            resultJson.validate[Seq[Response]] match {
              case JsSuccess(responseList, _) => Right(responseList)
              case JsError(_) => Left(APIError.BadAPIResponse(result.status, message.getOrElse("Unknown error")))
            }
          }
        }
        .recover { //case _: WSResponse =>
          case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
        }
    }
  }

  def createUpdate(url: String, requestBody: JsObject)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val personalToken: Option[String]= sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request: WSRequest = ws.url(url)
    val requestWithAuth: WSRequest = personalToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token", "Accept" -> "application/vnd.github.v3+json")
      case None => request.addHttpHeaders("Accept" -> "application/vnd.github.v3+json")
    }
    val response = requestWithAuth.put(requestBody)

    EitherT {
      response.map {
          result => {
            val resultBody: JsValue = Json.parse(result.body)
            val message: Option[String] = (resultBody \ "message").asOpt[String]
            result.status match {
              case 200 | 201 => Right(resultBody)
              case 403 => Left(APIError.BadAPIResponse(403, "Authentication failed"))
              case 404 => Left(APIError.BadAPIResponse(404, "User or repository not found"))
              case 409 => Left(APIError.BadAPIResponse(409, "sha does not match"))
              case 422 => Left(APIError.BadAPIResponse(422, message.getOrElse("Unknown error: Could not create or update file")))
              // previous approach: match on message
              // case Some("path contains a malformed path component") | Some("path cannot start with a slash") => Left(APIError.BadAPIResponse(422, "Invalid path"))
              // case Some("Invalid request.\n\n\"sha\" wasn't supplied.") => Left(APIError.BadAPIResponse(422, "File already exists"))
              // case _ => Left(APIError.BadAPIResponse(422, "Could not delete file"))
              case _ => Left(APIError.BadAPIResponse(500, "Unknown error: Could not create or update file"))
            }
          }
        }
        .recover { //case _: WSResponse =>
          case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
        }
    }
  }

  def delete(url: String, requestBody: JsObject)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val personalToken: Option[String] = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request: WSRequest = ws.url(url)
    val requestWithAuth: WSRequest = personalToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token", "Accept" -> "application/vnd.github.v3+json")
      case None => request.addHttpHeaders("Accept" -> "application/vnd.github.v3+json")
    }
    // delete() method doesn't support a body
    val response = requestWithAuth.withBody(requestBody).execute("DELETE")

    EitherT {
      response.map {
          result => {
            val resultBody: JsValue = Json.parse(result.body)
            val message: Option[String] = (resultBody \ "message").asOpt[String]
            result.status match {
              case 200 => Right(resultBody)
              case 403 => Left(APIError.BadAPIResponse(403, "Authentication failed"))
              case 404 => Left(APIError.BadAPIResponse(404, "Path not found")) // including if file doesn't exist
              case 409 => Left(APIError.BadAPIResponse(409, "sha does not match"))
              case 422 => Left(APIError.BadAPIResponse(422, message.getOrElse("Unknown error: Could not delete file")))
              // previous approach: match on message
              // case Some("path contains a malformed path component") | Some("path cannot start with a slash") => Left(APIError.BadAPIResponse(422, "Invalid path"))
              // case _ => Left(APIError.BadAPIResponse(422, "Could not delete file"))
              case _ => Left(APIError.BadAPIResponse(500, "Unknown error: Could not delete file"))
            }
          }
        }
        .recover { //case _: WSResponse =>
          case _ => Left(APIError.BadAPIResponse(500, "Could not connect"))
        }
    }
  }
}
