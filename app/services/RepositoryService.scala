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
    val missingErrorText = "Missing required value"
    val invalidTypeErrorText = "Invalid data type"
    val reqBodyValuesEither: Either[String, (String, Instant, Int, Int)] = for {
      // if any required value is missing, the result is Left(missingErrorText)
      username <- reqBody.flatMap(_.get("username").flatMap(_.headOption)).toRight(missingErrorText)
      accountCreatedStr <- reqBody.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).toRight(missingErrorText)
      numFollowersStr <- reqBody.flatMap(_.get("numFollowers").flatMap(_.headOption)).toRight(missingErrorText)
      numFollowingStr <- reqBody.flatMap(_.get("numFollowing").flatMap(_.headOption)).toRight(missingErrorText)
      // if any data type is invalid, the result is Left(invalidTypeErrorText)
      accountCreated <- Try(Instant.parse(accountCreatedStr)).toOption.toRight(invalidTypeErrorText)
      numFollowers <- Try(numFollowersStr.toInt).toOption.toRight(invalidTypeErrorText)
      numFollowing <- Try(numFollowingStr.toInt).toOption.toRight(invalidTypeErrorText)
    } yield (username, accountCreated, numFollowers, numFollowing)

    // location can be blank
    val location: String = reqBody.flatMap(_.get("location").flatMap(_.headOption)).getOrElse("")
    //    val username: String = reqBody.flatMap(_.get("username").flatMap(_.headOption)).get
    //    val accountCreated: Instant = Instant.parse(reqBody.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).get)
    //    val numFollowers: Int = reqBody.flatMap(_.get("numFollowers").flatMap(_.headOption)).get.toInt
    //    val numFollowing: Int = reqBody.flatMap(_.get("numFollowing").flatMap(_.headOption)).get.toInt

    reqBodyValuesEither match {
      case Right((username, accountCreated, numFollowers, numFollowing)) => {
        val user = UserModel(username, location, accountCreated, numFollowers, numFollowing)
        repositoryTrait.create(user)
      }
      case Left(errorText) => Future.successful(Left(APIError.BadAPIResponse(400, errorText)))
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
      case Failure(_) => Future.successful(Left(APIError.BadAPIResponse(500, "Invalid field to update")))
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
