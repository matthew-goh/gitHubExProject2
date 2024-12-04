package controllers

import models.{CreateRequestBody, DeleteRequestBody, FileInfo, RepoItem, UpdateRequestBody, UserModel}
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf.CSRF
import services.{GithubService, RepositoryService}

import java.util.Base64
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(repoService: RepositoryService, service: GithubService, val controllerComponents: ControllerComponents)
                                     (implicit ec: ExecutionContext) extends BaseController with play.api.i18n.I18nSupport {
  ///// METHODS CALLED BY FRONTEND /////
  def accessToken()(implicit request: Request[_]): Option[CSRF.Token] = {
    CSRF.getToken
  }

//  def formatDateTime(instant: Instant): String = {
//    val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
//    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
//    zonedDateTime.format(formatter)
//  }

  def invalidRoute(path: String): Action[AnyContent] = Action.async {_ =>
    Future.successful(NotFound(views.html.pagenotfound(path)))
  }

  def listAllUsers(): Action[AnyContent] = Action.async {_ =>
    repoService.index().map{
      case Right(userList: Seq[UserModel]) => Ok(views.html.userlisting(userList))
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  def getUserDetails(username: String): Action[AnyContent] = Action.async { implicit request =>
    service.getGithubUser(username = username).value.map {
      case Right(user) => {
        val userModel = service.convertToUserModel(user)
        Ok(views.html.usersearch(userModel))
      }
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  def searchUser(): Action[AnyContent] = Action.async { implicit request =>
    accessToken()
    val usernameSubmitted: Option[String] = request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption))
    usernameSubmitted match {
      case None | Some("") => Future.successful(BadRequest(views.html.unsuccessful("No username provided")))
      case Some(username) => Future.successful(Redirect(routes.ApplicationController.getUserDetails(username)))
    }
  }

  def addUser(): Action[AnyContent] = Action.async { implicit request =>
    accessToken()
    // request.body.asFormUrlEncoded has type Option[Map[String, Seq[String]]]
    repoService.create(request.body.asFormUrlEncoded).map{
      case Right(_) => Ok(views.html.confirmation("User added"))
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  def deleteUser(username: String): Action[AnyContent] = Action.async { _ =>
    repoService.delete(username).map{
      case Right(_) => Ok(views.html.confirmation("User removed from database"))
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  // test-only
  def deleteAll(): Action[AnyContent] = Action.async { _ =>
    repoService.deleteAll().map{
      case Right(deleteResult) => deleteResult.getDeletedCount match {
        case 0 => Ok(views.html.confirmation("No users in database. Action completed"))
        case _ => Ok(views.html.confirmation("All users removed from database")) }
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  def getUserRepos(username: String): Action[AnyContent] = Action.async { _ =>
    service.getGithubRepos(username = username).value.map {
      case Right(repoList) => Ok(views.html.userrepos(repoList, username))
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

  def getRepoItems(username: String, repoName: String): Action[AnyContent] = Action.async { _ =>
    service.getRepoItems(username = username, repoName = repoName).value.map {
      case Right(repoItemList) => Ok(views.html.repoitems(repoItemList, username, repoName))
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }

//  def getFromPath(username: String, repoName: String, path: String): Action[AnyContent] = Action.async { _ =>
//    // first try getRepoItems to see if the path is a folder
//    // use flatMap and Future.successful() here so that the inner map returns the required Future[Result], not Future[Future[Result]]
//    service.getRepoItems(username = username, repoName = repoName, path = path).value.flatMap {
//      case Right(repoItemList) => Future.successful(Ok(views.html.foldercontents(repoItemList, username, repoName, path)))
//      case Left(e) if e.httpResponseStatus == 500 => Future.successful(InternalServerError(views.html.unsuccessful(e.reason)))
//      case Left(_) => {
//        // if there is an error, see if the path is a file
//        service.getFileInfo(username = username, repoName = repoName, path = path).value.map {
//          case Right(file) => Ok(views.html.filedetails(file, username, repoName))
//          case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
//        }
//      }
//    }
//  }

  def getFromPath(username: String, repoName: String, path: String): Action[AnyContent] = Action.async { _ =>
    service.getFolderOrFile(username, repoName, path).map{
      case Right(contents) => contents match {
        case file: FileInfo => Ok(views.html.filedetails(file, username, repoName))
        case repoItemList: Seq[RepoItem] => Ok(views.html.foldercontents(repoItemList, username, repoName, path))
        case _ => InternalServerError(views.html.unsuccessful("Unexpected type returned by service method"))
      }
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }


  ///// METHODS TO MODIFY GITHUB /////
  // create - using curl request
  def createFile(username: String, repoName: String, path: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[CreateRequestBody] match {
      case JsSuccess(requestBody, _) =>
        service.createGithubFile(username = username, repoName = repoName, path = path, body = requestBody).value.map{
          case Right(response) => Created {response}
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        }
      case JsError(_) => Future.successful(BadRequest {"Invalid request body"})
    }
  }

  // create - using form
  def showCreateForm(username: String, repoName: String, folderPath: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(views.html.createfile(username, repoName, folderPath, CreateRequestBody.createForm)))
  }
  def createFormSubmit(username: String, repoName: String, folderPath: Option[String]): Action[AnyContent] =  Action.async { implicit request =>
    accessToken()
    CreateRequestBody.createForm.bindFromRequest().fold( //from the implicit request we want to bind this to the form in our companion object
      formWithErrors => {
        Future.successful(BadRequest(views.html.createfile(username, repoName, folderPath, formWithErrors)))
      },
      formData => {  // formData is a CreateRequestBody
        val path: String = folderPath match {
          case None => formData.fileName
          case Some(folder) => s"$folder/${formData.fileName}"
        } //if (folderPath.isEmpty) formData.fileName else s"$folderPath/${formData.fileName}"
        service.processRequestFromForm(username = username, repoName = repoName, path = path, body = formData).value.map{
          case Right(_) => Redirect(routes.ApplicationController.getFromPath(username, repoName, path))
          case Left(error) => error.httpResponseStatus match {
            case 422 if error.reason == "Bad response from upstream: Invalid request.\n\n\"sha\" wasn't supplied." =>
              UnprocessableEntity(views.html.unsuccessful("File already exists"))
            case _ => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
          }
        }
      }
    )
  }

  // update - using curl request
  def updateFile(username: String, repoName: String, path: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[UpdateRequestBody] match {
      case JsSuccess(requestBody, _) =>
        service.updateGithubFile(username = username, repoName = repoName, path = path, body = requestBody).value.map{
          case Right(response) => Ok {response}
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        }
      case JsError(_) => Future.successful(BadRequest {"Invalid request body"})
    }
  }

  // update - using form
  def showUpdateForm(username: String, repoName: String, filePath: String): Action[AnyContent] = Action.async { implicit request =>
    service.getFileInfo(username = username, repoName = repoName, path = filePath).value.map {
      case Right(file) => {
        val decodedContent = new String(Base64.getDecoder.decode(file.content.replaceAll("\n", "")))
        val formWithDetails = UpdateRequestBody.updateForm.fill(UpdateRequestBody(commitMessage = "", newFileContent = decodedContent, fileSHA = file.sha))
        Ok(views.html.updatefile(username, repoName, filePath, formWithDetails))
      }
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }
  def updateFormSubmit(username: String, repoName: String, filePath: String): Action[AnyContent] =  Action.async { implicit request =>
    accessToken()
    UpdateRequestBody.updateForm.bindFromRequest().fold( //from the implicit request we want to bind this to the form in our companion object
      formWithErrors => {
        Future.successful(BadRequest(views.html.updatefile(username, repoName, filePath, formWithErrors)))
      },
      formData => {  // formData is an UpdateRequestBody
        service.processRequestFromForm(username = username, repoName = repoName, path = filePath, body = formData).value.map{
          case Right(_) => Redirect(routes.ApplicationController.getFromPath(username, repoName, filePath))
          case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
        }
      }
    )
  }

  // delete - using curl request
  def deleteFile(username: String, repoName: String, path: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[DeleteRequestBody] match {
      case JsSuccess(requestBody, _) =>
        service.deleteGithubFile(username = username, repoName = repoName, path = path, body = requestBody).value.map{
          case Right(response) => Ok {response}
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        }
      case JsError(_) => Future.successful(BadRequest {"Invalid request body"})
    }
  }

  // delete - using form
  def showDeleteForm(username: String, repoName: String, filePath: String): Action[AnyContent] = Action.async { implicit request =>
    service.getFileInfo(username = username, repoName = repoName, path = filePath).value.map {
      case Right(file) => {
        val formWithDetails = DeleteRequestBody.deleteForm.fill(DeleteRequestBody(commitMessage = "", fileSHA = file.sha))
        Ok(views.html.deletefile(username, repoName, filePath, formWithDetails))
      }
      case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
    }
  }
  def deleteFormSubmit(username: String, repoName: String, filePath: String): Action[AnyContent] =  Action.async { implicit request =>
    accessToken()
    DeleteRequestBody.deleteForm.bindFromRequest().fold( //from the implicit request we want to bind this to the form in our companion object
      formWithErrors => {
        Future.successful(BadRequest(views.html.deletefile(username, repoName, filePath, formWithErrors)))
      },
      formData => {  // formData is a DeleteRequestBody
        service.processRequestFromForm(username = username, repoName = repoName, path = filePath, body = formData).value.map{
          case Right(_) => Ok(views.html.confirmationdelete(username, repoName, filePath))
          case Left(error) => Status(error.httpResponseStatus)(views.html.unsuccessful(error.reason))
        }
      }
    )
  }


  ///// REPOSITORY API METHODS WITHOUT FRONTEND /////
  def index(): Action[AnyContent] = Action.async { _ =>
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
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        }
      // dataRepository.create() is a Future[Either[APIError.BadAPIResponse, DataModel]
      case JsError(_) => Future.successful(BadRequest {"Invalid request body"}) // ensure correct return type
    }
  }

  def read(username: String): Action[AnyContent] = Action.async { _ =>
    repoService.read(username).map{ // dataRepository.read() is a Future[Either[APIError, DataModel]]
      case Right(item) => Ok {Json.toJson(item)}
      case Left(error) => Status(error.httpResponseStatus)(error.reason)
    }
  }

  def update(username: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[UserModel] match {
      case JsSuccess(userModel, _) =>
        repoService.update(username, userModel).map{
          case Right(_) => Accepted {Json.toJson(request.body)}
          case Left(error) => Status(error.httpResponseStatus)(error.reason)
        } // dataRepository.update() is a Future[Either[APIError, result.UpdateResult]]
      case JsError(_) => Future.successful(BadRequest {"Invalid request body"})
    }
  }
  def updateWithValue(username: String, field: String, newValue: String): Action[AnyContent] = Action.async { _ =>
    repoService.updateWithValue(username, field, newValue).map{
      case Right(_) => Accepted {s"$field of user $username has been updated to: $newValue"}
      case Left(error) => Status(error.httpResponseStatus)(error.reason)
    }
  }

  def delete(username: String): Action[AnyContent] = Action.async { _ =>
    repoService.delete(username).map{
      case Right(_) => Accepted {s"$username has been deleted from the database"}
      case Left(error) => Status(error.httpResponseStatus)(error.reason)
    } // dataRepository.delete() is a Future[Either[APIError, result.DeleteResult]]
  }
}
