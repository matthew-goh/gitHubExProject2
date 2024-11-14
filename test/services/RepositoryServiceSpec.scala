package services

import baseSpec.BaseSpec
import com.mongodb.client.result._
import models.{APIError, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import repositories.DataRepositoryTrait

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class RepositoryServiceSpec extends BaseSpec with MockFactory with ScalaFutures with GuiceOneAppPerSuite {
  val mockRepoTrait: DataRepositoryTrait = mock[DataRepositoryTrait]
  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  val testRepoService = new RepositoryService(mockRepoTrait)

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

  "index" should {
    "return a list of DataModels" in {
      (mockRepoTrait.index _)
        .expects()
        .returning(Future(Right(Seq(userModel, userModel2)))) // explicitly states what the connector method returns
        .once() // how many times we can expect this response

      // allows for the result to be waited for as the Future type can be seen as a placeholder for a value we don't have yet
      // -no need .value since output is a Future, not EitherT
      whenReady(testRepoService.index()) { result =>
        result shouldBe Right(Seq(userModel, userModel2))
      }
    }

    "return an error" in {
      (mockRepoTrait.index _)
        .expects()
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 404, and got reason: Repository not found"))))
        .once()

      whenReady(testRepoService.index()) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 404, and got reason: Repository not found"))
      }
    }
  }

  "create" should {
    "return a DataModel" in {
      (mockRepoTrait.create(_: UserModel))
        .expects(userModel)
        .returning(Future(Right(userModel)))
        .once()

      whenReady(testRepoService.create(userModel)) { result =>
        result shouldBe Right(userModel)
      }
    }

    "return an error" in {
      (mockRepoTrait.create(_: UserModel))
        .expects(*)
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to add book"))))
        .once()

      whenReady(testRepoService.create(userModel)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to add book"))
      }
    }
  }
}
