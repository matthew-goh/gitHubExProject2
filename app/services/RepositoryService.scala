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

  def read(id: String): Future[Either[APIError, UserModel]] = {
    repositoryTrait.read(id)
  }
}
