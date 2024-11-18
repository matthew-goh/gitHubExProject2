package controllers

import baseSpec.BaseSpecWithApplication
import cats.data.EitherT
import models.{APIError, GithubRepo, RepoItem, User, UserModel}
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
  private val userModel2: UserModel = UserModel(
    "user2",
    "",
    Instant.parse("2022-11-07T09:42:16Z"),
    24,
    13
  )

  ///// METHODS CALLED BY FRONTEND /////
  "ApplicationController .listAllUsers()" should {
    "list all users in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)
      val request2: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel2))
      val createdResult2: Future[Result] = TestApplicationController.create()(request2)

      val listingResult: Future[Result] = TestApplicationController.listAllUsers()(FakeRequest())
      status(listingResult) shouldBe Status.OK
      contentAsString(listingResult) should include ("user1")
      contentAsString(listingResult) should include ("Account created: 07 Nov 2022 09:42")
      afterEach()
    }

    "show 'no users found' if the database is empty" in {
      beforeEach()
      val listingResult: Future[Result] = TestApplicationController.listAllUsers()(FakeRequest())
      status(listingResult) shouldBe Status.OK
      contentAsString(listingResult) should include ("No users found")
      afterEach()
    }
  }

  "ApplicationController .getUserDetails()" should {
    "display the user's details" in {
      (mockGithubService.getGithubUser(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testAPIResult.as[User]))
        .once()

      (mockGithubService.convertToUserModel(_: User))
        .expects(*)
        .returning(GithubServiceSpec.testAPIUserModel)
        .once()

      // testRequest.fakeRequest includes CRSFToken - needed if resulting page has a POST form
      val searchResult: Future[Result] = TestApplicationController.getUserDetails(username = "matthew-goh")(testRequest.fakeRequest)
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Username: matthew-goh")
      contentAsString(searchResult) should include ("Account created: 28 Oct 2024 15:22")
    }

    "return a NotFound if the user is not found" in {
      (mockGithubService.getGithubUser(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getUserDetails(username = "??")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("User not found")
    }
  }

  "ApplicationController .searchUser()" should {
    "redirect to user details page when a username is searched" in {
      val searchRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/searchuser").withFormUrlEncodedBody(
        "username" -> "user1"
      )
      val searchResult: Future[Result] = TestApplicationController.searchUser()(searchRequest)
      status(searchResult) shouldBe Status.SEE_OTHER
      redirectLocation(searchResult) shouldBe Some("/github/users/user1")
    }
  }

  "ApplicationController .addUser()" should {
    "add a user to the database" in {
      beforeEach()
      val addUserRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/add").withFormUrlEncodedBody(
        "username" -> "user1",
        "location" -> "",
        "accountCreatedTime" -> "2024-10-28T15:22:40Z",
        "numFollowers" -> "0",
        "numFollowing" -> "2"
      ) // .withCRSFToken not needed?
      val addUserResult: Future[Result] = TestApplicationController.addUser()(addUserRequest)
      status(addUserResult) shouldBe Status.OK
      contentAsString(addUserResult) should include ("Addition of user successful!")
      afterEach()
    }

    "return a BadRequest if the user is already in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val addUserRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/add").withFormUrlEncodedBody(
        "username" -> "user1",
        "location" -> "",
        "accountCreatedTime" -> "2024-10-28T15:22:40Z",
        "numFollowers" -> "0",
        "numFollowing" -> "2"
      )
      val addUserResult: Future[Result] = TestApplicationController.addUser()(addUserRequest)
      status(addUserResult) shouldBe Status.BAD_REQUEST
      contentAsString(addUserResult) should include ("User already exists in database")
      afterEach()
    }
  }

  "ApplicationController .deleteUser()" should {
    "delete a user from the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val deleteResult: Future[Result] = TestApplicationController.deleteUser("user1")(FakeRequest())
      status(deleteResult) shouldBe Status.OK
      contentAsString(deleteResult) should include ("Delete successful!")
    }

    "return a BadRequest if the user could not be found" in {
      beforeEach()
      val deleteResult: Future[Result] = TestApplicationController.deleteUser("user1")(FakeRequest())
      status(deleteResult) shouldBe Status.BAD_REQUEST
      contentAsString(deleteResult) should include ("User not found in database")
      afterEach()
    }
  }

  "ApplicationController .getUserRepos()" should {
    "list the user's repositories" in {
      (mockGithubService.getGithubRepos(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testAPIRepoResult.as[Seq[GithubRepo]]))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getUserRepos(username = "matthew-goh")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Public Repositories of matthew-goh")
      contentAsString(searchResult) should include ("gitHubExProject2")
      contentAsString(searchResult) should include ("play-template")
    }

    "return a NotFound if the user is not found" in {
      (mockGithubService.getGithubRepos(_: Option[String], _: String)(_: ExecutionContext))
        .expects(None, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getUserRepos(username = "??")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("User not found")
    }
  }

  "ApplicationController .getRepoItems()" should {
    "list the repository items" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String)(_: ExecutionContext))
        .expects(None, *, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testRepoItemsJson.as[Seq[RepoItem]]))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getRepoItems(username = "matthew-goh", repoName = "scala101")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Repository scala101 by matthew-goh")
      contentAsString(searchResult) should include (".gitignore")
      contentAsString(searchResult) should include ("src")
    }

    "return a NotFound if the repository is not found" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String)(_: ExecutionContext))
        .expects(None, *, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getRepoItems(username = "matthew-goh", repoName = "abc")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("User or repository not found")
    }
  }

  ///// API METHODS WITHOUT FRONTEND /////
  "ApplicationController .index()" should {
    "list all users in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      Thread.sleep(100)
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
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))

      val createdResult: Future[Result] = TestApplicationController.create()(request)
      status(createdResult) shouldBe Status.CREATED
      contentAsJson(createdResult).as[UserModel] shouldBe userModel
      afterEach()
    }

    "return a BadRequest if the user is already in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val duplicateRequest: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val duplicateResult: Future[Result] = TestApplicationController.create()(duplicateRequest)
      status(duplicateResult) shouldBe Status.BAD_REQUEST
      contentAsString(duplicateResult) shouldBe "Bad response from upstream; got status: 500, and got reason: User already exists in database"
      afterEach()
    }

    "return a BadRequest if the request body could not be parsed into a DataModel" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson("abcd"))
      val createdResult: Future[Result] = TestApplicationController.create()(request)
      status(createdResult) shouldBe Status.BAD_REQUEST
      contentAsString(createdResult) shouldBe "Invalid request body"
      afterEach()
    }
  }

  "ApplicationController .read()" should {
    "find a user in the database by username" in {
      beforeEach()
      // need to use .create before we can find something in our repository
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      Thread.sleep(100)
      val readResult: Future[Result] = TestApplicationController.read("user1")(FakeRequest())
      status(readResult) shouldBe Status.OK
      contentAsJson(readResult).as[UserModel] shouldBe userModel
      afterEach()
    }

    "return a NotFound if the user could not be found" in {
      beforeEach()
      val readResult: Future[Result] = TestApplicationController.read("aaaa")(FakeRequest())
      status(readResult) shouldBe NOT_FOUND
      contentAsString(readResult) shouldBe "Bad response from upstream; got status: 404, and got reason: User not found"
      afterEach()
    }
  }

  "ApplicationController .update()" should {
    "update a user in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val updateRequest: FakeRequest[JsValue] = testRequest.buildPut("/api/${userModel.username}").withBody[JsValue](Json.toJson(newUserModel))
      val updateResult = TestApplicationController.update("user1")(updateRequest)
      status(updateResult) shouldBe Status.ACCEPTED
      contentAsJson(updateResult).as[UserModel] shouldBe newUserModel
      afterEach()
    }

    "return a BadRequest if the if the request body could not be parsed into a DataModel" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val badUpdateRequest: FakeRequest[JsValue] = testRequest.buildPut("/api/${userModel.username}").withBody[JsValue](Json.toJson("abcd"))
      val badUpdateResult = TestApplicationController.update("user1")(badUpdateRequest)
      status(badUpdateResult) shouldBe Status.BAD_REQUEST
      contentAsString(badUpdateResult) shouldBe "Invalid request body"
      afterEach()
    }

    "add the user to the database if they could not be found" in { // upsert(true)
      beforeEach()
      val updateRequest: FakeRequest[JsValue] = testRequest.buildPut("/api/${userModel.username}").withBody[JsValue](Json.toJson(newUserModel))
      val updateResult = TestApplicationController.update("user1")(updateRequest) // Future(<not completed>)
      status(updateResult) shouldBe Status.ACCEPTED
      contentAsJson(updateResult).as[UserModel] shouldBe newUserModel
      afterEach()
    }
  }

  "ApplicationController .updateWithValue()" should {
    "update a user's location in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val updateResult = TestApplicationController.updateWithValue("user1", "location", "London")(FakeRequest())
      status(updateResult) shouldBe Status.ACCEPTED
      contentAsString(updateResult) shouldBe "location of user user1 has been updated to: London"
      afterEach()
    }

    "update a user's number of followers in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val updateResult = TestApplicationController.updateWithValue("user1", "numFollowers", "20")(FakeRequest())
      status(updateResult) shouldBe Status.ACCEPTED
      contentAsString(updateResult) shouldBe "numFollowers of user user1 has been updated to: 20"
      afterEach()
    }

    "return a BadRequest if an invalid field is specified" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val readResult: Future[Result] = TestApplicationController.updateWithValue("user1", "bad", "qqq")(FakeRequest())
      status(readResult) shouldBe Status.BAD_REQUEST
      contentAsString(readResult) shouldBe "Bad response from upstream; got status: 500, and got reason: Invalid field to update"
      afterEach()
    }

    "return a BadRequest if number following is updated with a non-integer value" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val readResult: Future[Result] = TestApplicationController.updateWithValue("user1", "numFollowing", "x5")(FakeRequest())
      status(readResult) shouldBe Status.BAD_REQUEST
      contentAsString(readResult) shouldBe "Bad response from upstream; got status: 500, and got reason: New value must be an integer"
      afterEach()
    }

    "return a BadRequest if the user does not exist in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val readResult: Future[Result] = TestApplicationController.updateWithValue("aaaa", "numFollowers", "1")(FakeRequest())
      status(readResult) shouldBe Status.BAD_REQUEST
      contentAsString(readResult) shouldBe "Bad response from upstream; got status: 404, and got reason: User not found"
      afterEach()
    }
  }

  "ApplicationController .delete()" should {
    "delete a user in the database" in {
      beforeEach()
      val request: FakeRequest[JsValue] = testRequest.buildPost("/api").withBody[JsValue](Json.toJson(userModel))
      val createdResult: Future[Result] = TestApplicationController.create()(request)

      val deleteResult: Future[Result] = TestApplicationController.delete("user1")(FakeRequest())
      status(deleteResult) shouldBe Status.ACCEPTED
      contentAsString(deleteResult) shouldBe "user1 has been deleted from the database"

      // check that database is now empty
      val indexResult: Future[Result] = TestApplicationController.index()(FakeRequest())
      status(indexResult) shouldBe Status.OK
      contentAsJson(indexResult).as[Seq[UserModel]] shouldBe Seq()
      afterEach()
    }

    "return a BadRequest if the user could not be found" in {
      beforeEach()
      val deleteResult: Future[Result] = TestApplicationController.delete("user1")(FakeRequest())
      status(deleteResult) shouldBe Status.BAD_REQUEST
      contentAsString(deleteResult) shouldBe "Bad response from upstream; got status: 404, and got reason: User not found"
      afterEach()
    }
  }

  override def beforeEach(): Unit = await(repository.deleteAll())
  override def afterEach(): Unit = await(repository.deleteAll())
}
