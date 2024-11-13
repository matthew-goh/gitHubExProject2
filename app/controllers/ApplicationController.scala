package controllers

import models.UserModel
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf.CSRF
import services.{GithubService, RepositoryService}

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(repoService: RepositoryService, service: GithubService, val controllerComponents: ControllerComponents)
                                     (implicit ec: ExecutionContext) extends BaseController with play.api.i18n.I18nSupport {
  ///// METHODS CALLED BY FRONTEND /////
  def accessToken(implicit request: Request[_]) = {
    CSRF.getToken
  }

  def formatDateTime(instant: Instant): String = {
    val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
    zonedDateTime.format(formatter)
  }

  def searchUser(username: String): Action[AnyContent] = Action.async {implicit request =>
    service.getGithubUser(username = username).value.map {
      case Right(user) => {
        val userModel = service.convertToUserModel(user)
        Ok(views.html.usersearch(userModel, formatDateTime(userModel.accountCreatedTime)))
      }
      case Left(error) => BadRequest(views.html.unsuccessful("User not found"))
    }
  }

  ///// API METHODS WITHOUT FRONTEND /////
  def index(): Action[AnyContent] = Action.async { implicit request =>
    repoService.index().map{ // dataRepository.index() is a Future[Either[APIError.BadAPIResponse, Seq[DataModel]]]
      case Right(item: Seq[UserModel]) => Ok {Json.toJson(item)}
      case Left(error) => Status(error.httpResponseStatus)(error.reason)
    }
  }

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[UserModel] match { // valid or invalid request
      case JsSuccess(userModel, _) =>
        repoService.create(userModel).map{
          case Right(_) => Created {request.body}
          case Left(error) => BadRequest {error.reason}
        }
      // dataRepository.create() is a Future[Either[APIError.BadAPIResponse, DataModel]
      case JsError(_) => Future(BadRequest {"Invalid request body"}) // ensure correct return type
    }
  }
}
