package controllers

import baseSpec.BaseSpecWithApplication
import cats.data.EitherT
import models.{APIError, User, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import play.api.http.Status
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import services.{GithubService, GithubServiceSpec}

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
    "",
    Instant.parse("2024-10-28T15:22:40Z"),
    0,
    2
  )
  private val newUserModel: UserModel = UserModel(
    "user1",
    "London",
    Instant.parse("2024-10-28T15:22:40Z"),
    0,
    2
  )

  ///// METHODS CALLED BY FRONTEND /////
  "searchUser" should {
    "display the user's details" in {
      (mockGithubService.getGithubUser(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testAPIResult.as[User]))
        .once()

      (mockGithubService.convertToUserModel(_: User))
        .expects(*)
        .returning(GithubServiceSpec.testAPIUserModel)
        .once()

      val searchResult: Future[Result] = TestApplicationController.searchUser(username = "matthew-goh")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Username: matthew-goh")
      contentAsString(searchResult) should include ("Account created: 28 Oct 2024 15:22")
    }

    "return a BadRequest" in {
      (mockGithubService.getGithubUser(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(500, "Could not connect")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.searchUser(username = "??")(FakeRequest())
      status(searchResult) shouldBe BAD_REQUEST
      contentAsString(searchResult) should include ("User not found")
    }
  }

  ///// API METHODS WITHOUT FRONTEND /////
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
