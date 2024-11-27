package connectors

import cats.data.EitherT
import models.APIError
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class GithubConnector @Inject()(ws: WSClient) {
  def get[Response](url: String)(implicit rds: OFormat[Response], ec: ExecutionContext): EitherT[Future, APIError, Response] = {
//    val githubToken = sys.env.get("GITHUB_TOKEN")
    val personalToken = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = personalToken match {
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
            // TODO: use validate here, no need message option
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
//    val githubToken = sys.env.get("GITHUB_TOKEN")
    val personalToken = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = personalToken match {
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

  def createUpdate(url: String, requestBody: JsObject)(implicit ec: ExecutionContext): EitherT[Future, APIError, JsValue] = {
    val personalToken = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = personalToken match {
      case Some(token) => request.addHttpHeaders("Authorization" -> s"Bearer $token", "Accept" -> "application/vnd.github.v3+json")
      case None => request.addHttpHeaders("Accept" -> "application/vnd.github.v3+json")
    }
    val response = requestWithAuth.put(requestBody)

    EitherT {
      response.map {
          result => {
            val resultBody: JsValue = Json.parse(result.body)
            val message: Option[String] = (resultBody \ "message").asOpt[String]
//            println(s"${result.status} $resultBody \n $message")
            result.status match {
              case 200 | 201 => Right(resultBody)
              case 403 => Left(APIError.BadAPIResponse(403, "Authentication failed"))
              case 404 => Left(APIError.BadAPIResponse(404, "User or repository not found"))
              case 409 => Left(APIError.BadAPIResponse(409, "sha does not match"))
              case 422 => { Left(APIError.BadAPIResponse(422, message.getOrElse("Unknown error: Could not create or update file")))
//                message match {
//                  case Some("path contains a malformed path component") | Some("path cannot start with a slash") => Left(APIError.BadAPIResponse(422, "Invalid path"))
//                  case Some("Invalid request.\n\n\"sha\" wasn't supplied.") => Left(APIError.BadAPIResponse(422, "File already exists"))
//                  case _ => Left(APIError.BadAPIResponse(422, "Could not create or update file"))
//                }
              }
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
    val personalToken = sys.env.get("PERSONAL_GITHUB_TOKEN")

    val request = ws.url(url)
    val requestWithAuth = personalToken match {
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
//            println(s"${result.status} $resultBody \n $message")
            result.status match {
              case 200 => Right(resultBody)
              case 403 => Left(APIError.BadAPIResponse(403, "Authentication failed"))
              case 404 => Left(APIError.BadAPIResponse(404, "Not found")) // including if file doesn't exist
              case 409 => Left(APIError.BadAPIResponse(409, "sha does not match"))
              case 422 => { Left(APIError.BadAPIResponse(422, message.getOrElse("Unknown error: Could not delete file")))
//                message match {
//                  case Some("path contains a malformed path component") | Some("path cannot start with a slash") => Left(APIError.BadAPIResponse(422, "Invalid path"))
//                  case _ => Left(APIError.BadAPIResponse(422, "Could not delete file"))
//                }
              }
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
