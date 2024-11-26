package controllers

import baseSpec.BaseSpecWithApplication
import cats.data.EitherT
import models.{APIError, CreateRequestBody, DeleteRequestBody, FileInfo, GithubRepo, RepoItem, UpdateRequestBody, User, UserModel}
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
      contentAsString(addUserResult) should include ("User added successfully!")
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
      contentAsString(deleteResult) should include ("User removed successfully!")
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
        .expects(None, "matthew-goh", *)
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
        .expects(None, "??", *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getUserRepos(username = "??")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("User not found")
    }
  }

  "ApplicationController .getRepoItems()" should {
    "list the repository items" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "scala101", "", *)
        .returning(EitherT.rightT(GithubServiceSpec.testRepoItemsJson.as[Seq[RepoItem]]))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getRepoItems(username = "matthew-goh", repoName = "scala101")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Repository scala101 by matthew-goh")
      contentAsString(searchResult) should include (".gitignore")
      contentAsString(searchResult) should include ("src")
    }

    "return a NotFound if the repository is not found" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "abc", "", *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getRepoItems(username = "matthew-goh", repoName = "abc")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("User or repository not found")
    }
  }

  "ApplicationController .getFromPath()" should {
    "list the folder's items if the path is a folder" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "scala101", "src", *)
        .returning(EitherT.rightT(GithubServiceSpec.testRepoItemsJson.as[Seq[RepoItem]]))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getFromPath(username = "matthew-goh", repoName = "scala101", path = "src")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Contents of folder: <i>src</i>")
      contentAsString(searchResult) should include (".gitignore")
      contentAsString(searchResult) should include ("project")
    }

    "display the file's contents if the path is a file" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "scala101", "src/main/scala/Hello.scala", *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(500, "Could not connect")))
        .once()

      (mockGithubService.getFileInfo(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "scala101", "src/main/scala/Hello.scala", *)
        .returning(EitherT.rightT(GithubServiceSpec.testFileInfoJson.as[FileInfo]))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getFromPath(username = "matthew-goh", repoName = "scala101", path = "src/main/scala/Hello.scala")(FakeRequest())
      status(searchResult) shouldBe OK
      contentAsString(searchResult) should include ("Details of <i>Hello.scala</i>")
      contentAsString(searchResult) should include ("<b>Path:</b> scala101/src/main/scala/Hello.scala")
      contentAsString(searchResult) should include ("object Hello extends App")
    }

    "return a NotFound if the path is invalid" in {
      (mockGithubService.getRepoItems(_: Option[String], _: String, _: String, _: String)(_: ExecutionContext))
        .expects(None, "matthew-goh", "scala101", "badpath", *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val searchResult: Future[Result] = TestApplicationController.getFromPath(username = "matthew-goh", repoName = "scala101", path = "badpath")(FakeRequest())
      status(searchResult) shouldBe NOT_FOUND
      contentAsString(searchResult) should include ("Path not found")
    }
  }


  ///// METHODS TO MODIFY GITHUB /////
  "ApplicationController .createFile()" should {
    val body = CreateRequestBody("testfile.txt", "Test commit", "Test file content")

    "create a file on GitHub" in {
      (mockGithubService.createGithubFile(_: Option[String], _: String, _: String, _: String, _: CreateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "testfile.txt", body, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/create/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(body))
      val createFileResult: Future[Result] = TestApplicationController.createFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(createFileResult) shouldBe Status.CREATED
      contentAsJson(createFileResult) shouldBe GithubServiceSpec.testCreateResult
    }

    "return a NotFound if the username or repository does not exist" in {
      (mockGithubService.createGithubFile(_: Option[String], _: String, _: String, _: String, _: CreateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "abc", "testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "User or repository not found")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/create/matthew-goh/repos/abc/testfile.txt").withBody[JsValue](Json.toJson(body))
      val createFileResult: Future[Result] = TestApplicationController.createFile("matthew-goh", "abc", "testfile.txt")(request)
      status(createFileResult) shouldBe Status.NOT_FOUND
      contentAsString(createFileResult) shouldBe "User or repository not found"
    }

    "return a BadRequest if another API error occurred" in {
      (mockGithubService.createGithubFile(_: Option[String], _: String, _: String, _: String, _: CreateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "invalid//testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(422, "Invalid path")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/create/matthew-goh/repos/test-repo/invalid//testfile.txt").withBody[JsValue](Json.toJson(body))
      val createFileResult: Future[Result] = TestApplicationController.createFile("matthew-goh", "test-repo", "invalid//testfile.txt")(request)
      status(createFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(createFileResult) shouldBe "Bad response from upstream; got status: 422, and got reason: Invalid path"
    }

    "return a BadRequest if the request body could not be parsed into a CreatedRequestBody" in {
      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/create/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson("abcd"))
      val createFileResult: Future[Result] = TestApplicationController.createFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(createFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(createFileResult) shouldBe "Invalid request body"
    }
  }

  "ApplicationController .createFormSubmit()" should {
    val body = CreateRequestBody("folder2/testfile.txt", "Test commit", "Test file content")

    "create a file on GitHub" in {
      (mockGithubService.processRequestFromForm[CreateRequestBody](_: Option[String], _: String, _: String, _: String, _: CreateRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[CreateRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "folder1/folder2/testfile.txt", body, *, mockGithubService.ValidRequest.CreateRequest)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      val createFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/create/form").withFormUrlEncodedBody(
        "fileName" -> "folder2/testfile.txt",
        "commitMessage" -> "Test commit",
        "fileContent" -> "Test file content"
      )
      val createFileResult: Future[Result] = TestApplicationController.createFormSubmit("matthew-goh", "test-repo", "folder1")(createFileRequest)
      status(createFileResult) shouldBe Status.SEE_OTHER
      redirectLocation(createFileResult) shouldBe Some("/github/users/matthew-goh/repos/test-repo/folder1/folder2/testfile.txt")
    }

    "detect a form with errors" in {
      val createFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/create/form").withFormUrlEncodedBody(
        "fileName" -> "testfile.txt",
        "commitMessage" -> "",
        "fileContent" -> "text"
      )
      val createFileResult: Future[Result] = TestApplicationController.createFormSubmit("matthew-goh", "test-repo", "")(createFileRequest)
      status(createFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(createFileResult) should include ("testfile.txt")
    }

    "detect an invalid file name" in {
      val createFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/create/form").withFormUrlEncodedBody(
        "fileName" -> "invalid//file",
        "commitMessage" -> "Test commit",
        "fileContent" -> "text"
      )
      val createFileResult: Future[Result] = TestApplicationController.createFormSubmit("matthew-goh", "test-repo", "")(createFileRequest)
      status(createFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(createFileResult) should include ("invalid//file")
    }

    "return a BadRequest if the file already exists" in {
      (mockGithubService.processRequestFromForm[CreateRequestBody](_: Option[String], _: String, _: String, _: String, _: CreateRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[CreateRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "folder2/testfile.txt", body, *, mockGithubService.ValidRequest.CreateRequest)
        .returning(EitherT.leftT(APIError.BadAPIResponse(422, "File already exists")))
        .once()

      val createFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/create/form").withFormUrlEncodedBody(
        "fileName" -> "folder2/testfile.txt",
        "commitMessage" -> "Test commit",
        "fileContent" -> "Test file content"
      )
      val createFileResult: Future[Result] = TestApplicationController.createFormSubmit("matthew-goh", "test-repo", "")(createFileRequest)
      status(createFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(createFileResult) should include ("File already exists")
    }
  }

  "ApplicationController .updateFile()" should {
    val body = UpdateRequestBody("Test commit", "Test file content", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")

    "update a file on GitHub" in {
      (mockGithubService.updateGithubFile(_: Option[String], _: String, _: String, _: String, _: UpdateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "testfile.txt", body, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/update/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(body))
      val updateFileResult: Future[Result] = TestApplicationController.updateFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(updateFileResult) shouldBe Status.OK
      contentAsJson(updateFileResult) shouldBe GithubServiceSpec.testCreateResult
    }

    "return a NotFound if the username or repository does not exist" in {
      (mockGithubService.updateGithubFile(_: Option[String], _: String, _: String, _: String, _: UpdateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "abc", "testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "User or repository not found")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/update/matthew-goh/repos/abc/testfile.txt").withBody[JsValue](Json.toJson(body))
      val updateFileResult: Future[Result] = TestApplicationController.updateFile("matthew-goh", "abc", "testfile.txt")(request)
      status(updateFileResult) shouldBe Status.NOT_FOUND
      contentAsString(updateFileResult) shouldBe "User or repository not found"
    }

    "return a BadRequest if another API error occurred" in {
      (mockGithubService.updateGithubFile(_: Option[String], _: String, _: String, _: String, _: UpdateRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(409, "sha does not match")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/update/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(body))
      val updateFileResult: Future[Result] = TestApplicationController.updateFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(updateFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(updateFileResult) shouldBe "Bad response from upstream; got status: 409, and got reason: sha does not match"
    }

    "return a BadRequest if the request body could not be parsed into an UpdateRequestBody" in {
      val createBody = CreateRequestBody("testfile.txt", "Test commit", "Test file content") // create instead of update
      val request: FakeRequest[JsValue] = testRequest.buildPut("/github/update/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(createBody))
      val updateFileResult: Future[Result] = TestApplicationController.updateFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(updateFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(updateFileResult) shouldBe "Invalid request body"
    }
  }

  "ApplicationController .updateFormSubmit()" should {
    val body = UpdateRequestBody("Test commit", "New file content", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")

    "update a file on GitHub" in {
      (mockGithubService.processRequestFromForm[UpdateRequestBody](_: Option[String], _: String, _: String, _: String, _: UpdateRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[UpdateRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "folder1/testfile.txt", body, *, mockGithubService.ValidRequest.UpdateRequest)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      val updateFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/update/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> "Test commit",
        "newFileContent" -> "New file content"
      )
      val updateFileResult: Future[Result] = TestApplicationController.updateFormSubmit("matthew-goh", "test-repo", "folder1/testfile.txt")(updateFileRequest)
      status(updateFileResult) shouldBe Status.SEE_OTHER
      redirectLocation(updateFileResult) shouldBe Some("/github/users/matthew-goh/repos/test-repo/folder1/testfile.txt")
    }

    "detect a form with errors" in {
      val updateFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/update/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> "",
        "newFileContent" -> "New file content"
      )
      val updateFileResult: Future[Result] = TestApplicationController.updateFormSubmit("matthew-goh", "test-repo", "folder1/testfile.txt")(updateFileRequest)
      status(updateFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(updateFileResult) should include ("New file content")
    }

    "return a BadRequest if the update failed" in {
      (mockGithubService.processRequestFromForm[UpdateRequestBody](_: Option[String], _: String, _: String, _: String, _: UpdateRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[UpdateRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "folder1/testfile.txt", body, *, mockGithubService.ValidRequest.UpdateRequest)
        .returning(EitherT.leftT(APIError.BadAPIResponse(403, "Authentication failed")))
        .once()

      val updateFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/update/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> "Test commit",
        "newFileContent" -> "New file content"
      )
      val updateFileResult: Future[Result] = TestApplicationController.updateFormSubmit("matthew-goh", "test-repo", "folder1/testfile.txt")(updateFileRequest)
      status(updateFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(updateFileResult) should include ("Bad response from upstream; got status: 403, and got reason: Authentication failed")
    }
  }

  "ApplicationController .deleteFile()" should {
    val body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")

    "delete a file on GitHub" in {
      (mockGithubService.deleteGithubFile(_: Option[String], _: String, _: String, _: String, _: DeleteRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "testfile.txt", body, *)
        .returning(EitherT.rightT(GithubServiceSpec.testDeleteResult))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildDelete("/github/delete/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(body))
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(deleteFileResult) shouldBe Status.OK
      contentAsJson(deleteFileResult) shouldBe GithubServiceSpec.testDeleteResult
    }

    "return a NotFound if the username, repository or file does not exist" in {
      (mockGithubService.deleteGithubFile(_: Option[String], _: String, _: String, _: String, _: DeleteRequestBody)(_: ExecutionContext))
        .expects(None, "abc", "test-repo", "testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildDelete("/github/delete/abc/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(body))
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFile("abc", "test-repo", "testfile.txt")(request)
      status(deleteFileResult) shouldBe Status.NOT_FOUND
      contentAsString(deleteFileResult) shouldBe "Not found"
    }

    "return a BadRequest if another API error occurred" in {
      (mockGithubService.deleteGithubFile(_: Option[String], _: String, _: String, _: String, _: DeleteRequestBody)(_: ExecutionContext))
        .expects(None, "matthew-goh", "test-repo", "/testfile.txt", body, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(422, "Invalid path")))
        .once()

      val request: FakeRequest[JsValue] = testRequest.buildDelete("/github/delete/matthew-goh/repos/test-repo//testfile.txt").withBody[JsValue](Json.toJson(body))
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFile("matthew-goh", "test-repo", "/testfile.txt")(request)
      status(deleteFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(deleteFileResult) shouldBe "Bad response from upstream; got status: 422, and got reason: Invalid path"
    }

    "return a BadRequest if the request body could not be parsed into a DeleteRequestBody" in {
      val createBody = CreateRequestBody("testfile.txt", "Test commit", "Test file content") // create instead of delete
      val request: FakeRequest[JsValue] = testRequest.buildDelete("/github/delete/matthew-goh/repos/test-repo/testfile.txt").withBody[JsValue](Json.toJson(createBody))
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFile("matthew-goh", "test-repo", "testfile.txt")(request)
      status(deleteFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(deleteFileResult) shouldBe "Invalid request body"
    }
  }

  "ApplicationController .deleteFormSubmit()" should {
    val body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")

    "delete a file on GitHub" in {
      (mockGithubService.processRequestFromForm[DeleteRequestBody](_: Option[String], _: String, _: String, _: String, _: DeleteRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[DeleteRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "folder1/testfile.txt", body, *, mockGithubService.ValidRequest.DeleteRequest)
        .returning(EitherT.rightT(GithubServiceSpec.testDeleteResult))
        .once()

      val deleteFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/delete/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> "Test delete"
      )
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFormSubmit("matthew-goh", "test-repo", "folder1/testfile.txt")(deleteFileRequest)
      status(deleteFileResult) shouldBe Status.OK
      contentAsString(deleteFileResult) should include ("File deleted successfully!")
    }

    "detect a form with errors" in {
      val deleteFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/delete/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> ""
      )
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFormSubmit("matthew-goh", "test-repo", "folder1/testfile.txt")(deleteFileRequest)
      status(deleteFileResult) shouldBe Status.BAD_REQUEST
      contentAsString(deleteFileResult) should include ("4753fddcf141a3798b6aed0e81f56c7f14535ed7")
    }

    "return a NotFound if the file is not found" in {
      (mockGithubService.processRequestFromForm[DeleteRequestBody](_: Option[String], _: String, _: String, _: String, _: DeleteRequestBody)(_: ExecutionContext, _: mockGithubService.ValidRequest[DeleteRequestBody]))
        .expects(None, "matthew-goh", "test-repo", "abc.txt", body, *, mockGithubService.ValidRequest.DeleteRequest)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      val deleteFileRequest: FakeRequest[AnyContentAsFormUrlEncoded] = testRequest.buildPost("/github/delete/form").withFormUrlEncodedBody(
        "fileSHA" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7",
        "commitMessage" -> "Test delete"
      )
      val deleteFileResult: Future[Result] = TestApplicationController.deleteFormSubmit("matthew-goh", "test-repo", "abc.txt")(deleteFileRequest)
      status(deleteFileResult) shouldBe Status.NOT_FOUND
      contentAsString(deleteFileResult) should include ("Path not found")
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
