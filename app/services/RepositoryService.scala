package services

import models.{APIError, UserModel}
import org.mongodb.scala.result
import repositories.DataRepositoryTrait

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

class RepositoryService @Inject()(repositoryTrait: DataRepositoryTrait){

}
