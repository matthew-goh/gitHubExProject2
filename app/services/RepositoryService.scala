package services

import models.{APIError, UserModel}
import org.mongodb.scala.result
import repositories.DataRepositoryTrait

import java.time.Instant
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class RepositoryService @Inject()(repositoryTrait: DataRepositoryTrait){

  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]] = {
    repositoryTrait.index()
  }

  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    repositoryTrait.create(user)
  }
  // version called by ApplicationController addUser()
  def create(reqBody: Option[Map[String, Seq[String]]]): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    val username: String = reqBody.flatMap(_.get("username").flatMap(_.headOption)).get
    val location: String = reqBody.flatMap(_.get("location").flatMap(_.headOption)).getOrElse("")
    val accountCreated: Instant = Instant.parse(reqBody.flatMap(_.get("accountCreatedTime").flatMap(_.headOption)).get)
    val numFollowers: Int = reqBody.flatMap(_.get("numFollowers").flatMap(_.headOption)).get.toInt
    val numFollowing: Int = reqBody.flatMap(_.get("numFollowing").flatMap(_.headOption)).get.toInt
    // TODO: for comprehension, safe toInt?

    val user = UserModel(username, location, accountCreated, numFollowers, numFollowing)
    repositoryTrait.create(user)
  }

  def read(username: String): Future[Either[APIError, UserModel]] = {
    repositoryTrait.read(username)
  }

  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]] = {
    repositoryTrait.update(username, user)
  }

  def updateWithValue(username: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]] = {
    repositoryTrait.updateWithValue(username, field, newValue)
  }

  def delete(username: String): Future[Either[APIError, result.DeleteResult]] = {
    repositoryTrait.delete(username)
  }
}
