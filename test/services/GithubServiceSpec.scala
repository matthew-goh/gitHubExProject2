package services

import baseSpec.BaseSpec
import cats.data.EitherT
import connectors.GithubConnector
import models.{APIError, User, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class GithubServiceSpec extends BaseSpec with MockFactory with ScalaFutures with GuiceOneAppPerSuite {
  val mockConnector = mock[GithubConnector]
  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  val testService = new GithubService(mockConnector)

  "getGithubUser" should {
    val url: String = "testUrl"

    "return a Collection" in {
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
      val url: String = "testUrl"

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
}
