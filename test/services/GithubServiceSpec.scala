package services

import baseSpec.BaseSpec
import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, CreateRequestBody, DeleteRequestBody, FileInfo, GithubRepo, RepoItem, UpdateRequestBody, User, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._

import java.time.Instant
import java.util.Base64
import scala.concurrent.ExecutionContext

class GithubServiceSpec extends BaseSpec with MockFactory with ScalaFutures with GuiceOneAppPerSuite {
  val mockConnector = mock[GithubConnector]
  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  val testService = new GithubService(mockConnector)

  "getGithubUser" should {
    val url: String = "testUrl"

    "return a user's details" in {
      (mockConnector.get[User](_: String)(_: OFormat[User], _: ExecutionContext))
        .expects(url, *, *) // can take *, which shows that the connector can expect any request in place of the parameter. You might sometimes see this as any().
        .returning(EitherT.rightT(GithubServiceSpec.testAPIResult.as[User])) // explicitly states what the connector method returns
        .once() // how many times we can expect this response

      // allows for the result to be waited for as the Future type can be seen as a placeholder for a value we don't have yet
      whenReady(testService.getGithubUser(urlOverride = Some(url), username = "matthew-goh").value) { result =>
        result shouldBe Right(GithubServiceSpec.testAPIUser)
      }
    }

    "return an error" in {
      (mockConnector.get[User](_: String)(_: OFormat[User], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(500, "Could not connect")))// How do we return an error?
        .once()

      whenReady(testService.getGithubUser(urlOverride = Some(url), username = "??").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Could not connect"))
      }
    }
  }

  "convertToUserModel" should {
    "return a UserModel with the correct field values" in {
      testService.convertToUserModel(GithubServiceSpec.testAPIUser) shouldBe GithubServiceSpec.testAPIUserModel
    }
    "return a UserModel with empty description if location is null" in {
      testService.convertToUserModel(GithubServiceSpec.testAPIUserWithLoc) shouldBe GithubServiceSpec.testAPIUserModelWithLoc
    }
  }

  "getGithubRepos" should {
    val url: String = "testUrl"

    "return a list of GitHub repositories" in {
      (mockConnector.getList[GithubRepo](_: String)(_: OFormat[GithubRepo], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testAPIRepoResult.as[Seq[GithubRepo]]))
        .once()

      whenReady(testService.getGithubRepos(urlOverride = Some(url), username = "matthew-goh").value) { result =>
        result shouldBe Right(Seq(GithubServiceSpec.testAPIRepo1, GithubServiceSpec.testAPIRepo2))
      }
    }

    "return an error" in {
      (mockConnector.getList[GithubRepo](_: String)(_: OFormat[GithubRepo], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      whenReady(testService.getGithubRepos(urlOverride = Some(url), username = "??").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Not found"))
      }
    }
  }

  "getRepoItems" should {
    val url: String = "testUrl"

    "return a list of files and folders in a GitHub repository" in {
      (mockConnector.getList[RepoItem](_: String)(_: OFormat[RepoItem], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testRepoItemsJson.as[Seq[RepoItem]]))
        .once()

      whenReady(testService.getRepoItems(urlOverride = Some(url), username = "matthew-goh", repoName = "scala101").value) { result =>
        result shouldBe Right(GithubServiceSpec.testRepoItemsList)
      }
    }

    "return an error" in {
      (mockConnector.getList[RepoItem](_: String)(_: OFormat[RepoItem], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      whenReady(testService.getRepoItems(urlOverride = Some(url), username = "??", repoName = "abc").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Not found"))
      }
    }
  }

  "getFileInfo" should {
    val url: String = "testUrl"

    "return a user's details" in {
      (mockConnector.get[FileInfo](_: String)(_: OFormat[FileInfo], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.rightT(GithubServiceSpec.testFileInfoJson.as[FileInfo]))
        .once()

      whenReady(testService.getFileInfo(urlOverride = Some(url), username = "matthew-goh", repoName = "scala101",
        path = "src/main/scala/Hello.scala").value) { result =>
        result shouldBe Right(GithubServiceSpec.testFileInfo)
      }
    }

    "return an error" in {
      (mockConnector.get[FileInfo](_: String)(_: OFormat[FileInfo], _: ExecutionContext))
        .expects(url, *, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Not found")))
        .once()

      whenReady(testService.getFileInfo(urlOverride = Some(url), username = "matthew-goh", repoName = "scala101",
        path = "badpath").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Not found"))
      }
    }
  }

  "createGithubFile" should {
    val url: String = "testUrl"
    val requestBody = Json.obj(
      "message" -> "Test commit",
      "content" -> Base64.getEncoder.encodeToString("Test file content".getBytes("UTF-8"))
    )

    "return a JsValue for a successful call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      whenReady(testService.createGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = CreateRequestBody("testfile.txt", "Test commit", "Test file content")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testCreateResult)
      }
    }

    "return an error" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(422, "path contains a malformed path component")))
        .once()

      whenReady(testService.createGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = CreateRequestBody("file.txt", "Test commit", "Test file content")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(422, "path contains a malformed path component"))
      }
    }
  }

  "updateGithubFile" should {
    val url: String = "testUrl"
    val requestBody = Json.obj(
      "message" -> "Test commit",
      "content" -> Base64.getEncoder.encodeToString("Test file content".getBytes("UTF-8")),
      "sha" -> "3eed7ec08d20f5749d88b819d20e0be5775a7e3b"
    )

    "return a JsValue for a successful call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      whenReady(testService.updateGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = UpdateRequestBody("Test commit", "Test file content", "3eed7ec08d20f5749d88b819d20e0be5775a7e3b")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testCreateResult)
      }
    }

    "return an error" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(409, "sha does not match")))
        .once()

      whenReady(testService.updateGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = UpdateRequestBody("Test commit", "Test file content", "3eed7ec08d20f5749d88b819d20e0be5775a7e3b")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(409, "sha does not match"))
      }
    }
  }

  "deleteGithubFile" should {
    val url: String = "testUrl"
    val requestBody = Json.obj(
      "message" -> "Test delete",
      "sha" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7"
    )

    "return a JsValue for a successful call" in {
      (mockConnector.delete(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testDeleteResult))
        .once()

      whenReady(testService.deleteGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testDeleteResult)
      }
    }

    "return an error" in {
      (mockConnector.delete(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, requestBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Path not found")))
        .once()

      whenReady(testService.deleteGithubFile(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Path not found"))
      }
    }
  }

  "processRequestFromForm" should {
    val url: String = "testUrl"
    val createBody = Json.obj(
      "message" -> "Test commit",
      "content" -> Base64.getEncoder.encodeToString("Test file content".getBytes("UTF-8"))
    )
    val updateBody = Json.obj(
      "message" -> "Test commit",
      "content" -> Base64.getEncoder.encodeToString("Test file content".getBytes("UTF-8")),
      "sha" -> "3eed7ec08d20f5749d88b819d20e0be5775a7e3b"
    )
    val deleteBody = Json.obj(
      "message" -> "Test delete",
      "sha" -> "4753fddcf141a3798b6aed0e81f56c7f14535ed7"
    )

    "return a JsValue for a successful create call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, createBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = CreateRequestBody("testfile.txt", "Test commit", "Test file content")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testCreateResult)
      }
    }

    "return an error for a create call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, createBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(422, "path contains a malformed path component")))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = CreateRequestBody("file.txt", "Test commit", "Test file content")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(422, "path contains a malformed path component"))
      }
    }

    "return a JsValue for a successful update call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, updateBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testCreateResult))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = UpdateRequestBody("Test commit", "Test file content", "3eed7ec08d20f5749d88b819d20e0be5775a7e3b")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testCreateResult)
      }
    }

    "return an error for an update call" in {
      (mockConnector.createUpdate(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, updateBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(409, "sha does not match")))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = UpdateRequestBody("Test commit", "Test file content", "3eed7ec08d20f5749d88b819d20e0be5775a7e3b")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(409, "sha does not match"))
      }
    }

    "return a JsValue for a successful delete call" in {
      (mockConnector.delete(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, deleteBody, *)
        .returning(EitherT.rightT(GithubServiceSpec.testDeleteResult))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "testfile.txt", body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")).value) { result =>
        result shouldBe Right(GithubServiceSpec.testDeleteResult)
      }
    }

    "return an error for a delete call" in {
      (mockConnector.delete(_: String, _: JsObject)(_: ExecutionContext))
        .expects(url, deleteBody, *)
        .returning(EitherT.leftT(APIError.BadAPIResponse(404, "Path not found")))
        .once()

      whenReady(testService.processRequestFromForm(urlOverride = Some(url), username = "matthew-goh", repoName = "test-repo",
        path = "invalid//file.txt", body = DeleteRequestBody("Test delete", "4753fddcf141a3798b6aed0e81f56c7f14535ed7")).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Path not found"))
      }
    }
  }
}

object GithubServiceSpec {
  val testAPIResult: JsValue = Json.parse("""{
  "login": "matthew-goh",
  "id": 186605436,
  "node_id": "U_kgDOCx9ffA",
  "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
  "gravatar_id": "",
  "url": "https://api.github.com/users/matthew-goh",
  "html_url": "https://github.com/matthew-goh",
  "followers_url": "https://api.github.com/users/matthew-goh/followers",
  "following_url": "https://api.github.com/users/matthew-goh/following{/other_user}",
  "gists_url": "https://api.github.com/users/matthew-goh/gists{/gist_id}",
  "starred_url": "https://api.github.com/users/matthew-goh/starred{/owner}{/repo}",
  "subscriptions_url": "https://api.github.com/users/matthew-goh/subscriptions",
  "organizations_url": "https://api.github.com/users/matthew-goh/orgs",
  "repos_url": "https://api.github.com/users/matthew-goh/repos",
  "events_url": "https://api.github.com/users/matthew-goh/events{/privacy}",
  "received_events_url": "https://api.github.com/users/matthew-goh/received_events",
  "type": "User",
  "user_view_type": "public",
  "site_admin": false,
  "name": "Matthew Goh",
  "company": null,
  "blog": "",
  "location": null,
  "email": null,
  "hireable": null,
  "bio": null,
  "twitter_username": null,
  "public_repos": 5,
  "public_gists": 0,
  "followers": 0,
  "following": 0,
  "created_at": "2024-10-28T15:22:40Z",
  "updated_at": "2024-11-05T11:54:37Z"
}""")

  val testAPIResultWithLoc: JsValue = Json.parse("""{
  "login": "matthew-goh",
  "id": 186605436,
  "node_id": "U_kgDOCx9ffA",
  "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
  "gravatar_id": "",
  "url": "https://api.github.com/users/matthew-goh",
  "html_url": "https://github.com/matthew-goh",
  "followers_url": "https://api.github.com/users/matthew-goh/followers",
  "following_url": "https://api.github.com/users/matthew-goh/following{/other_user}",
  "gists_url": "https://api.github.com/users/matthew-goh/gists{/gist_id}",
  "starred_url": "https://api.github.com/users/matthew-goh/starred{/owner}{/repo}",
  "subscriptions_url": "https://api.github.com/users/matthew-goh/subscriptions",
  "organizations_url": "https://api.github.com/users/matthew-goh/orgs",
  "repos_url": "https://api.github.com/users/matthew-goh/repos",
  "events_url": "https://api.github.com/users/matthew-goh/events{/privacy}",
  "received_events_url": "https://api.github.com/users/matthew-goh/received_events",
  "type": "User",
  "user_view_type": "public",
  "site_admin": false,
  "name": "Matthew Goh",
  "company": null,
  "blog": "",
  "location": "London",
  "email": null,
  "hireable": null,
  "bio": null,
  "twitter_username": null,
  "public_repos": 5,
  "public_gists": 0,
  "followers": 0,
  "following": 0,
  "created_at": "2024-10-28T15:22:40Z",
  "updated_at": "2024-11-05T11:54:37Z"
}""")

  val testAPIUser: User = User("matthew-goh", None, Instant.parse("2024-10-28T15:22:40Z"), 0, 0)
  val testAPIUserWithLoc: User = User("matthew-goh", Some("London"), Instant.parse("2024-10-28T15:22:40Z"), 0, 0)

  val testAPIUserModel: UserModel = UserModel("matthew-goh", "", Instant.parse("2024-10-28T15:22:40Z"), 0, 0)
  val testAPIUserModelWithLoc: UserModel = UserModel("matthew-goh", "London", Instant.parse("2024-10-28T15:22:40Z"), 0, 0)

  val testAPIRepo1: GithubRepo = GithubRepo(887803973, "gitHubExProject2")
  val testAPIRepo2: GithubRepo = GithubRepo(883685534, "play-template")
  val testAPIRepoResult: JsValue = Json.parse("""
      |[
      |  {
      |    "id": 887803973,
      |    "node_id": "R_kgDONOrQRQ",
      |    "name": "gitHubExProject2",
      |    "full_name": "matthew-goh/gitHubExProject2",
      |    "private": false,
      |    "owner": {
      |      "login": "matthew-goh",
      |      "id": 186605436,
      |      "node_id": "U_kgDOCx9ffA",
      |      "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
      |      "gravatar_id": "",
      |      "url": "https://api.github.com/users/matthew-goh",
      |      "html_url": "https://github.com/matthew-goh",
      |      "followers_url": "https://api.github.com/users/matthew-goh/followers",
      |      "following_url": "https://api.github.com/users/matthew-goh/following{/other_user}",
      |      "gists_url": "https://api.github.com/users/matthew-goh/gists{/gist_id}",
      |      "starred_url": "https://api.github.com/users/matthew-goh/starred{/owner}{/repo}",
      |      "subscriptions_url": "https://api.github.com/users/matthew-goh/subscriptions",
      |      "organizations_url": "https://api.github.com/users/matthew-goh/orgs",
      |      "repos_url": "https://api.github.com/users/matthew-goh/repos",
      |      "events_url": "https://api.github.com/users/matthew-goh/events{/privacy}",
      |      "received_events_url": "https://api.github.com/users/matthew-goh/received_events",
      |      "type": "User",
      |      "user_view_type": "public",
      |      "site_admin": false
      |    },
      |    "html_url": "https://github.com/matthew-goh/gitHubExProject2",
      |    "description": null,
      |    "fork": false,
      |    "url": "https://api.github.com/repos/matthew-goh/gitHubExProject2",
      |    "forks_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/forks",
      |    "keys_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/keys{/key_id}",
      |    "collaborators_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/collaborators{/collaborator}",
      |    "teams_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/teams",
      |    "hooks_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/hooks",
      |    "issue_events_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/issues/events{/number}",
      |    "events_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/events",
      |    "assignees_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/assignees{/user}",
      |    "branches_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/branches{/branch}",
      |    "tags_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/tags",
      |    "blobs_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/git/blobs{/sha}",
      |    "git_tags_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/git/tags{/sha}",
      |    "git_refs_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/git/refs{/sha}",
      |    "trees_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/git/trees{/sha}",
      |    "statuses_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/statuses/{sha}",
      |    "languages_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/languages",
      |    "stargazers_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/stargazers",
      |    "contributors_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/contributors",
      |    "subscribers_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/subscribers",
      |    "subscription_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/subscription",
      |    "commits_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/commits{/sha}",
      |    "git_commits_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/git/commits{/sha}",
      |    "comments_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/comments{/number}",
      |    "issue_comment_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/issues/comments{/number}",
      |    "contents_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/contents/{+path}",
      |    "compare_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/compare/{base}...{head}",
      |    "merges_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/merges",
      |    "archive_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/{archive_format}{/ref}",
      |    "downloads_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/downloads",
      |    "issues_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/issues{/number}",
      |    "pulls_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/pulls{/number}",
      |    "milestones_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/milestones{/number}",
      |    "notifications_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/notifications{?since,all,participating}",
      |    "labels_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/labels{/name}",
      |    "releases_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/releases{/id}",
      |    "deployments_url": "https://api.github.com/repos/matthew-goh/gitHubExProject2/deployments",
      |    "created_at": "2024-11-13T10:10:04Z",
      |    "updated_at": "2024-11-14T18:26:14Z",
      |    "pushed_at": "2024-11-14T18:26:10Z",
      |    "git_url": "git://github.com/matthew-goh/gitHubExProject2.git",
      |    "ssh_url": "git@github.com:matthew-goh/gitHubExProject2.git",
      |    "clone_url": "https://github.com/matthew-goh/gitHubExProject2.git",
      |    "svn_url": "https://github.com/matthew-goh/gitHubExProject2",
      |    "homepage": null,
      |    "size": 68,
      |    "stargazers_count": 0,
      |    "watchers_count": 0,
      |    "language": "Scala",
      |    "has_issues": true,
      |    "has_projects": true,
      |    "has_downloads": true,
      |    "has_wiki": true,
      |    "has_pages": false,
      |    "has_discussions": false,
      |    "forks_count": 0,
      |    "mirror_url": null,
      |    "archived": false,
      |    "disabled": false,
      |    "open_issues_count": 0,
      |    "license": null,
      |    "allow_forking": true,
      |    "is_template": false,
      |    "web_commit_signoff_required": false,
      |    "topics": [
      |
      |    ],
      |    "visibility": "public",
      |    "forks": 0,
      |    "open_issues": 0,
      |    "watchers": 0,
      |    "default_branch": "main"
      |  },
      |  {
      |    "id": 883685534,
      |    "node_id": "R_kgDONKv4ng",
      |    "name": "play-template",
      |    "full_name": "matthew-goh/play-template",
      |    "private": false,
      |    "owner": {
      |      "login": "matthew-goh",
      |      "id": 186605436,
      |      "node_id": "U_kgDOCx9ffA",
      |      "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
      |      "gravatar_id": "",
      |      "url": "https://api.github.com/users/matthew-goh",
      |      "html_url": "https://github.com/matthew-goh",
      |      "followers_url": "https://api.github.com/users/matthew-goh/followers",
      |      "following_url": "https://api.github.com/users/matthew-goh/following{/other_user}",
      |      "gists_url": "https://api.github.com/users/matthew-goh/gists{/gist_id}",
      |      "starred_url": "https://api.github.com/users/matthew-goh/starred{/owner}{/repo}",
      |      "subscriptions_url": "https://api.github.com/users/matthew-goh/subscriptions",
      |      "organizations_url": "https://api.github.com/users/matthew-goh/orgs",
      |      "repos_url": "https://api.github.com/users/matthew-goh/repos",
      |      "events_url": "https://api.github.com/users/matthew-goh/events{/privacy}",
      |      "received_events_url": "https://api.github.com/users/matthew-goh/received_events",
      |      "type": "User",
      |      "user_view_type": "public",
      |      "site_admin": false
      |    },
      |    "html_url": "https://github.com/matthew-goh/play-template",
      |    "description": null,
      |    "fork": false,
      |    "url": "https://api.github.com/repos/matthew-goh/play-template",
      |    "forks_url": "https://api.github.com/repos/matthew-goh/play-template/forks",
      |    "keys_url": "https://api.github.com/repos/matthew-goh/play-template/keys{/key_id}",
      |    "collaborators_url": "https://api.github.com/repos/matthew-goh/play-template/collaborators{/collaborator}",
      |    "teams_url": "https://api.github.com/repos/matthew-goh/play-template/teams",
      |    "hooks_url": "https://api.github.com/repos/matthew-goh/play-template/hooks",
      |    "issue_events_url": "https://api.github.com/repos/matthew-goh/play-template/issues/events{/number}",
      |    "events_url": "https://api.github.com/repos/matthew-goh/play-template/events",
      |    "assignees_url": "https://api.github.com/repos/matthew-goh/play-template/assignees{/user}",
      |    "branches_url": "https://api.github.com/repos/matthew-goh/play-template/branches{/branch}",
      |    "tags_url": "https://api.github.com/repos/matthew-goh/play-template/tags",
      |    "blobs_url": "https://api.github.com/repos/matthew-goh/play-template/git/blobs{/sha}",
      |    "git_tags_url": "https://api.github.com/repos/matthew-goh/play-template/git/tags{/sha}",
      |    "git_refs_url": "https://api.github.com/repos/matthew-goh/play-template/git/refs{/sha}",
      |    "trees_url": "https://api.github.com/repos/matthew-goh/play-template/git/trees{/sha}",
      |    "statuses_url": "https://api.github.com/repos/matthew-goh/play-template/statuses/{sha}",
      |    "languages_url": "https://api.github.com/repos/matthew-goh/play-template/languages",
      |    "stargazers_url": "https://api.github.com/repos/matthew-goh/play-template/stargazers",
      |    "contributors_url": "https://api.github.com/repos/matthew-goh/play-template/contributors",
      |    "subscribers_url": "https://api.github.com/repos/matthew-goh/play-template/subscribers",
      |    "subscription_url": "https://api.github.com/repos/matthew-goh/play-template/subscription",
      |    "commits_url": "https://api.github.com/repos/matthew-goh/play-template/commits{/sha}",
      |    "git_commits_url": "https://api.github.com/repos/matthew-goh/play-template/git/commits{/sha}",
      |    "comments_url": "https://api.github.com/repos/matthew-goh/play-template/comments{/number}",
      |    "issue_comment_url": "https://api.github.com/repos/matthew-goh/play-template/issues/comments{/number}",
      |    "contents_url": "https://api.github.com/repos/matthew-goh/play-template/contents/{+path}",
      |    "compare_url": "https://api.github.com/repos/matthew-goh/play-template/compare/{base}...{head}",
      |    "merges_url": "https://api.github.com/repos/matthew-goh/play-template/merges",
      |    "archive_url": "https://api.github.com/repos/matthew-goh/play-template/{archive_format}{/ref}",
      |    "downloads_url": "https://api.github.com/repos/matthew-goh/play-template/downloads",
      |    "issues_url": "https://api.github.com/repos/matthew-goh/play-template/issues{/number}",
      |    "pulls_url": "https://api.github.com/repos/matthew-goh/play-template/pulls{/number}",
      |    "milestones_url": "https://api.github.com/repos/matthew-goh/play-template/milestones{/number}",
      |    "notifications_url": "https://api.github.com/repos/matthew-goh/play-template/notifications{?since,all,participating}",
      |    "labels_url": "https://api.github.com/repos/matthew-goh/play-template/labels{/name}",
      |    "releases_url": "https://api.github.com/repos/matthew-goh/play-template/releases{/id}",
      |    "deployments_url": "https://api.github.com/repos/matthew-goh/play-template/deployments",
      |    "created_at": "2024-11-05T11:57:18Z",
      |    "updated_at": "2024-11-14T18:32:53Z",
      |    "pushed_at": "2024-11-14T18:32:49Z",
      |    "git_url": "git://github.com/matthew-goh/play-template.git",
      |    "ssh_url": "git@github.com:matthew-goh/play-template.git",
      |    "clone_url": "https://github.com/matthew-goh/play-template.git",
      |    "svn_url": "https://github.com/matthew-goh/play-template",
      |    "homepage": null,
      |    "size": 536,
      |    "stargazers_count": 0,
      |    "watchers_count": 0,
      |    "language": "Scala",
      |    "has_issues": true,
      |    "has_projects": true,
      |    "has_downloads": true,
      |    "has_wiki": true,
      |    "has_pages": false,
      |    "has_discussions": false,
      |    "forks_count": 0,
      |    "mirror_url": null,
      |    "archived": false,
      |    "disabled": false,
      |    "open_issues_count": 0,
      |    "license": null,
      |    "allow_forking": true,
      |    "is_template": false,
      |    "web_commit_signoff_required": false,
      |    "topics": [
      |
      |    ],
      |    "visibility": "public",
      |    "forks": 0,
      |    "open_issues": 0,
      |    "watchers": 0,
      |    "default_branch": "main"
      |  }
      |]
    """.stripMargin)

  val testRepoItemsList: Seq[RepoItem] = Seq(RepoItem(".gitignore", ".gitignore", "ae66c9c4436c5dce22a6d1855552f6651ea11ef4", "file"),
    RepoItem("build.sbt", "build.sbt", "477a19d9089787571e77878c7fe0fc5b05541753", "file"),
    RepoItem("project", "project", "318820de6fa5c540fcdc7dcdb15e492ca36cd2fc", "dir"), RepoItem("src", "src", "a19a9c57da0f6bfdca670328671c1bff0c4cb67f", "dir"))
  val testRepoItemsJson: JsValue = Json.parse("""
      |[
      |  {
      |    "name": ".gitignore",
      |    "path": ".gitignore",
      |    "sha": "ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
      |    "size": 158,
      |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/.gitignore?ref=main",
      |    "html_url": "https://github.com/matthew-goh/scala101/blob/main/.gitignore",
      |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
      |    "download_url": "https://raw.githubusercontent.com/matthew-goh/scala101/main/.gitignore",
      |    "type": "file",
      |    "_links": {
      |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/.gitignore?ref=main",
      |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
      |      "html": "https://github.com/matthew-goh/scala101/blob/main/.gitignore"
      |    }
      |  },
      |  {
      |    "name": "build.sbt",
      |    "path": "build.sbt",
      |    "sha": "477a19d9089787571e77878c7fe0fc5b05541753",
      |    "size": 387,
      |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
      |    "html_url": "https://github.com/matthew-goh/scala101/blob/main/build.sbt",
      |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/477a19d9089787571e77878c7fe0fc5b05541753",
      |    "download_url": "https://raw.githubusercontent.com/matthew-goh/scala101/main/build.sbt",
      |    "type": "file",
      |    "_links": {
      |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
      |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/477a19d9089787571e77878c7fe0fc5b05541753",
      |      "html": "https://github.com/matthew-goh/scala101/blob/main/build.sbt"
      |    }
      |  },
      |  {
      |    "name": "project",
      |    "path": "project",
      |    "sha": "318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
      |    "size": 0,
      |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/project?ref=main",
      |    "html_url": "https://github.com/matthew-goh/scala101/tree/main/project",
      |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/trees/318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
      |    "download_url": null,
      |    "type": "dir",
      |    "_links": {
      |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/project?ref=main",
      |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/trees/318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
      |      "html": "https://github.com/matthew-goh/scala101/tree/main/project"
      |    }
      |  },
      |  {
      |    "name": "src",
      |    "path": "src",
      |    "sha": "a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
      |    "size": 0,
      |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/src?ref=main",
      |    "html_url": "https://github.com/matthew-goh/scala101/tree/main/src",
      |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/trees/a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
      |    "download_url": null,
      |    "type": "dir",
      |    "_links": {
      |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/src?ref=main",
      |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/trees/a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
      |      "html": "https://github.com/matthew-goh/scala101/tree/main/src"
      |    }
      |  }
      |]
      |""".stripMargin)

  val testFileInfo: FileInfo = FileInfo("Hello.scala", "src/main/scala/Hello.scala", "49583c41ef04c166308ca27bb7d02c61908113da",
    "b2JqZWN0IEhlbGxvIGV4dGVuZHMgQXBwIHsKICBwcmludGxuKCJIZWxsbywg\nV29ybGQhIikKfQo=\n")
  val testFileInfoJson: JsValue = Json.parse("""
      |{
      |  "name": "Hello.scala",
      |  "path": "src/main/scala/Hello.scala",
      |  "sha": "49583c41ef04c166308ca27bb7d02c61908113da",
      |  "size": 56,
      |  "url": "https://api.github.com/repos/matthew-goh/scala101/contents/src/main/scala/Hello.scala?ref=main",
      |  "html_url": "https://github.com/matthew-goh/scala101/blob/main/src/main/scala/Hello.scala",
      |  "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/49583c41ef04c166308ca27bb7d02c61908113da",
      |  "download_url": "https://raw.githubusercontent.com/matthew-goh/scala101/main/src/main/scala/Hello.scala",
      |  "type": "file",
      |  "content": "b2JqZWN0IEhlbGxvIGV4dGVuZHMgQXBwIHsKICBwcmludGxuKCJIZWxsbywg\nV29ybGQhIikKfQo=\n",
      |  "encoding": "base64",
      |  "_links": {
      |    "self": "https://api.github.com/repos/matthew-goh/scala101/contents/src/main/scala/Hello.scala?ref=main",
      |    "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/49583c41ef04c166308ca27bb7d02c61908113da",
      |    "html": "https://github.com/matthew-goh/scala101/blob/main/src/main/scala/Hello.scala"
      |  }
      |}
      |""".stripMargin)

  val testCreateResult: JsValue = Json.parse("""{"content":{"name":"testfile.txt","path":"testfile.txt","sha":"4753fddcf141a3798b6aed0e81f56c7f14535ed7","size":18,
"url":"https://api.github.com/repos/matthew-goh/test-repo/contents/testfile.txt?ref=main",
"html_url":"https://github.com/matthew-goh/test-repo/blob/main/testfile.txt",
"git_url":"https://api.github.com/repos/matthew-goh/test-repo/git/blobs/4753fddcf141a3798b6aed0e81f56c7f14535ed7",
"download_url":"https://raw.githubusercontent.com/matthew-goh/test-repo/main/testfile.txt",
"type":"file","_links":{"self":"https://api.github.com/repos/matthew-goh/test-repo/contents/testfile.txt?ref=main",
"git":"https://api.github.com/repos/matthew-goh/test-repo/git/blobs/4753fddcf141a3798b6aed0e81f56c7f14535ed7",
"html":"https://github.com/matthew-goh/test-repo/blob/main/testfile.txt"}},
"commit":{"sha":"30fc1672e979da86d88f45e7ce46dbef3bd59d31","node_id":"C_kwDONRSKnNoAKDMwZmMxNjcyZTk3OWRhODZkODhmNDVlN2NlNDZkYmVmM2JkNTlkMzE","url":"https://api.github.com/repos/matthew-goh/test-repo/git/commits/30fc1672e979da86d88f45e7ce46dbef3bd59d31","html_url":"https://github.com/matthew-goh/test-repo/commit/30fc1672e979da86d88f45e7ce46dbef3bd59d31",
"author":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T10:28:44Z"},"committer":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T10:28:44Z"},
"tree":{"sha":"3eccd62615f2ad1ccb13b5d3cf77f67dd71ee9a9","url":"https://api.github.com/repos/matthew-goh/test-repo/git/trees/3eccd62615f2ad1ccb13b5d3cf77f67dd71ee9a9"},"message":"Test commit","parents":[],
"verification":{"verified":false,"reason":"unsigned","signature":null,"payload":null,"verified_at":null}}}
""")

  val testDeleteResult: JsValue = Json.parse("""{"content":null,"commit":{"sha":"1c70ddaa5668b41bcdb78f376acd41ae3bcdc36f","node_id":"C_kwDONRSKnNoAKDFjNzBkZGFhNTY2OGI0MWJjZGI3OGYzNzZhY2Q0MWFlM2JjZGMzNmY",
"url":"https://api.github.com/repos/matthew-goh/test-repo/git/commits/1c70ddaa5668b41bcdb78f376acd41ae3bcdc36f","html_url":"https://github.com/matthew-goh/test-repo/commit/1c70ddaa5668b41bcdb78f376acd41ae3bcdc36f",
"author":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T14:55:49Z"},"committer":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T14:55:49Z"},
"tree":{"sha":"b60e7f292976b89040c114f9b584cb5c2625565c","url":"https://api.github.com/repos/matthew-goh/test-repo/git/trees/b60e7f292976b89040c114f9b584cb5c2625565c"},"message":"Test delete",
"parents":[{"sha":"7c5098a91b8d82764fdb42c716bef22a63cf097b","url":"https://api.github.com/repos/matthew-goh/test-repo/git/commits/7c5098a91b8d82764fdb42c716bef22a63cf097b","html_url":"https://github.com/matthew-goh/test-repo/commit/7c5098a91b8d82764fdb42c716bef22a63cf097b"}],
"verification":{"verified":false,"reason":"unsigned","signature":null,"payload":null,"verified_at":null}}}
""")
}
