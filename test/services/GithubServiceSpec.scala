package services

import baseSpec.BaseSpec
import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class GithubServiceSpec extends BaseSpec with MockFactory with ScalaFutures with GuiceOneAppPerSuite {
  val mockConnector = mock[GithubConnector]
  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  val testService = new GithubService(mockConnector)

}
