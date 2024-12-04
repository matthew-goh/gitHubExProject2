package services

import models.{APIError, UserModel}
import org.mongodb.scala.result
import repositories.{DataRepositoryTrait, UserModelFields}

import java.time.Instant
import javax.inject._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RepositoryService @Inject()(repositoryTrait: DataRepositoryTrait){

  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]] = {
    repositoryTrait.index()
  }

  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    repositoryTrait.create(user)
  }
  // version called by ApplicationController addUser()
  def create(reqBody: Option[Map[String, Seq[String]]]): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    val missingError = APIError.BadAPIResponse(400, "Missing required value")
    val invalidTypeError = APIError.BadAPIResponse(400, "Invalid data type")

    // location can be blank
    val location: String = reqBody.flatMap(_.get("location").flatMap(_.headOption)).getOrElse("")

    val reqBodyValuesEither: Either[APIError.BadAPIResponse, UserModel] = for {
      // if any required value is missing, the result is Left(missingError)
      username <- reqBody.flatMap(_.get("username").flatMap(_.headOption)).toRight(missingError)
      accountCreatedStr <- reqBody.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).toRight(missingError)
      numFollowersStr <- reqBody.flatMap(_.get("numFollowers").flatMap(_.headOption)).toRight(missingError)
      numFollowingStr <- reqBody.flatMap(_.get("numFollowing").flatMap(_.headOption)).toRight(missingError)
      // if any data type is invalid, the result is Left(invalidTypeError)
      accountCreated <- Try(Instant.parse(accountCreatedStr)).toOption.toRight(invalidTypeError)
      numFollowers <- Try(numFollowersStr.toInt).toOption.toRight(invalidTypeError)
      numFollowing <- Try(numFollowingStr.toInt).toOption.toRight(invalidTypeError)
    } yield UserModel(username, location, accountCreated, numFollowers, numFollowing)

    //    val username: String = reqBody.flatMap(_.get("username").flatMap(_.headOption)).get
    //    val accountCreated: Instant = Instant.parse(reqBody.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).get)
    //    val numFollowers: Int = reqBody.flatMap(_.get("numFollowers").flatMap(_.headOption)).get.toInt
    //    val numFollowing: Int = reqBody.flatMap(_.get("numFollowing").flatMap(_.headOption)).get.toInt

    reqBodyValuesEither match {
      case Right(user) => repositoryTrait.create(user)
      case Left(error) => Future.successful(Left(error))
    }
  }

  def read(username: String): Future[Either[APIError, UserModel]] = {
    repositoryTrait.read(username)
  }

  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]] = {
    repositoryTrait.update(username, user)
  }

  def updateWithValue(username: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]] = {
    val fieldTry: Try[UserModelFields.Value] = Try(UserModelFields.withName(field))
    fieldTry match {
      case Success(fieldName) => repositoryTrait.updateWithValue(username, fieldName, newValue)
      case Failure(_) => Future.successful(Left(APIError.BadAPIResponse(400, "Invalid field to update")))
    }
  }

  def delete(username: String): Future[Either[APIError, result.DeleteResult]] = {
    repositoryTrait.delete(username)
  }

  // test-only
  def deleteAll(): Future[Either[APIError, result.DeleteResult]] = {
    repositoryTrait.deleteAll()
  }
}
