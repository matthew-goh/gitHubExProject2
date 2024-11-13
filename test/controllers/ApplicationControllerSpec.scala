package controllers

import baseSpec.BaseSpecWithApplication
import cats.data.EitherT
import models.{APIError, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import play.api.http.Status
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import services.GithubService

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ApplicationControllerSpec extends BaseSpecWithApplication with MockFactory {
  val mockGithubService: GithubService = mock[GithubService]

  val TestApplicationController = new ApplicationController(
    repoService,
    mockGithubService,
    component // comes from BaseSpecWithApplication
  )

  private val userModel: UserModel = UserModel(
    "user1",
    None,
    Instant.parse("2024-10-28T15:22:40Z"),
    0,
    2
  )
  private val newUserModel: UserModel = UserModel(
    "user1",
    Some("London"),
    Instant.parse("2024-10-28T15:22:40Z"),
    0,
    2
  )

  "ApplicationController .index()" should {
    "list all users in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val indexResult: Future[Result] = TestApplicationController.index()(FakeRequest())
      status(indexResult) shouldBe Status.OK
      contentAsJson(indexResult).as[Seq[UserModel]] shouldBe Seq(userModel)
      afterEach()
    }
  }

  "ApplicationController .create()" should {
    "create a user in the database" in {
      beforeEach()
      val x = Json.toJson(userModel)
      println(x)
      val request: FakeRequest[JsValue] = buildPost("/api").withBody[JsValue](Json.toJson(userModel))

      val createdResult: Future[Result] = TestApplicationController.create()(request)
      status(createdResult) shouldBe Status.CREATED
      contentAsJson(createdResult).as[UserModel] shouldBe userModel
      afterEach()
    }

    "return a BadRequest if the user is already in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val duplicateRequest: FakeRequest[JsValue] = buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val duplicateResult: Future[Result] = TestApplicationController.create()(duplicateRequest)
      status(duplicateResult) shouldBe Status.BAD_REQUEST
      contentAsString(duplicateResult) shouldBe "Bad response from upstream; got status: 500, and got reason: User already exists in database"
      afterEach()
    }

    "return a BadRequest if the request body could not be parsed into a DataModel" in {
      beforeEach()
      val request: FakeRequest[JsValue] = buildPost("/api").withBody[JsValue](Json.toJson("abcd"))
      val createdResult: Future[Result] = TestApplicationController.create()(request)
      status(createdResult) shouldBe Status.BAD_REQUEST
      contentAsString(createdResult) shouldBe "Invalid request body"
      afterEach()
    }
  }

  override def beforeEach(): Unit = await(repository.deleteAll())
  override def afterEach(): Unit = await(repository.deleteAll())
}
