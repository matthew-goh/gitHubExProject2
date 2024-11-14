package services

import models.{APIError, UserModel}
import org.mongodb.scala.result
import repositories.DataRepositoryTrait

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class RepositoryService @Inject()(repositoryTrait: DataRepositoryTrait){

  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]] = {
    repositoryTrait.index()
  }

  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]] = {
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

  // delete a document
  def delete(username: String): Future[Either[APIError, result.DeleteResult]] = {
    repositoryTrait.delete(username)
  }
}
