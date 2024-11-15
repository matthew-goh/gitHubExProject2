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

  def listAllUsers(): Action[AnyContent] = Action.async {implicit request =>
    repoService.index().map{
      case Right(userList: Seq[UserModel]) => Ok(views.html.userlisting(userList))
      case Left(error) => Status(error.httpResponseStatus)(error.reason)
    }
  }

  def getUserDetails(username: String): Action[AnyContent] = Action.async {implicit request =>
    service.getGithubUser(username = username).value.map {
      case Right(user) => {
        val userModel = service.convertToUserModel(user)
        Ok(views.html.usersearch(userModel))
      }
      case Left(error) => { error.reason match {
        case "Bad response from upstream; got status: 404, and got reason: Not found" => NotFound(views.html.unsuccessful("User not found"))
        case _ => BadRequest(views.html.unsuccessful("Could not connect"))
      }}
    }
  }

  def searchUser(): Action[AnyContent] = Action.async {implicit request =>
    accessToken
    val username: String = request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption)).get
    Future.successful(Redirect(routes.ApplicationController.getUserDetails(username)))
  }

  def addUser(): Action[AnyContent] = Action.async {implicit request =>
    accessToken
    val username: String = request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption)).get
    val location: String = request.body.asFormUrlEncoded.flatMap(_.get("location").flatMap(_.headOption)).getOrElse("")
    val accountCreated: Instant = Instant.parse(request.body.asFormUrlEncoded.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).get)
    val numFollowers: Int = request.body.asFormUrlEncoded.flatMap(_.get("numFollowers").flatMap(_.headOption)).get.toInt
    val numFollowing: Int = request.body.asFormUrlEncoded.flatMap(_.get("numFollowing").flatMap(_.headOption)).get.toInt

    val user = UserModel(username, location, accountCreated, numFollowers, numFollowing)
    repoService.create(user).map{
      case Right(_) => Ok(views.html.confirmation("Addition of user"))
      case Left(error) => {
        error.reason match {
          case "Bad response from upstream; got status: 500, and got reason: User already exists in database"
          => BadRequest(views.html.unsuccessful("User already exists in database"))
          case _ => BadRequest("Unable to add user.")
        }
      }
    }
  }

  def deleteUser(username: String): Action[AnyContent] = Action.async { implicit request =>
    repoService.delete(username).map{
      case Right(_) => Ok(views.html.confirmation("Delete"))
      case Left(error) => BadRequest(views.html.unsuccessful("User not found in database"))
    }
  }

  def getUserRepos(username: String): Action[AnyContent] = Action.async {implicit request =>
    service.getGithubRepos(username = username).value.map {
      case Right(repoList) => Ok(views.html.userrepos(repoList, username))
      case Left(error) => { error.reason match {
        case "Bad response from upstream; got status: 404, and got reason: Not found" => NotFound(views.html.unsuccessful("User not found"))
        case _ => BadRequest(views.html.unsuccessful("Could not connect"))
      }}
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

  def read(username: String): Action[AnyContent] = Action.async { implicit request =>
    repoService.read(username).map{ // dataRepository.read() is a Future[Either[APIError, DataModel]]
      case Right(item) => Ok {Json.toJson(item)}
      case Left(error) => NotFound {error.reason}
    }
  }

  def update(username: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[UserModel] match {
      case JsSuccess(userModel, _) =>
        repoService.update(username, userModel).map{
          case Right(_) => Accepted {Json.toJson(request.body)}
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        } // dataRepository.update() is a Future[Either[APIError, result.UpdateResult]]
      case JsError(_) => Future(BadRequest {"Invalid request body"})
    }
  }
  def updateWithValue(username: String, field: String, newValue: String): Action[AnyContent] = Action.async { implicit request =>
    repoService.updateWithValue(username, field, newValue).map{
      case Right(_) => Accepted {s"$field of user $username has been updated to: $newValue"}
      case Left(error) => BadRequest {error.reason}
    }
  }

  def delete(username: String): Action[AnyContent] = Action.async { implicit request =>
    repoService.delete(username).map{
      case Right(_) => Accepted {s"$username has been deleted from the database"}
      case Left(error) => BadRequest {error.reason}
    } // dataRepository.delete() is a Future[Either[APIError, result.DeleteResult]]
  }
}
