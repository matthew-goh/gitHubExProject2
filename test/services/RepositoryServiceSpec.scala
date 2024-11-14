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
  private val testUpdateResult: UpdateResult = UpdateResult.acknowledged(1, 1, null)
  private val testDeleteResult: DeleteResult = DeleteResult.acknowledged(1)

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

  "read" should {
    "return a UserModel" in {
      (mockRepoTrait.read(_: String))
        .expects(*)
        .returning(Future(Right(userModel)))
        .once()

      whenReady(testRepoService.read("user1")) { result =>
        result shouldBe Right(userModel)
      }
    }

    "return an error" in {
      (mockRepoTrait.read(_: String))
        .expects(*)
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 404, and got reason: User not found"))))
        .once()

      whenReady(testRepoService.read("abcd")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 404, and got reason: User not found"))
      }
    }
  }

  "update" should {
    "return an UpdateResult" in {
      (mockRepoTrait.update(_: String, _: UserModel))
        .expects(*, *)
        .returning(Future(Right(testUpdateResult)))
        .once()

      whenReady(testRepoService.update("user1", newUserModel)) { result =>
        result shouldBe Right(testUpdateResult)
      }
    }

    "return an error" in {
      (mockRepoTrait.update(_: String, _: UserModel))
        .expects(*, *)
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to update user"))))
        .once()

      whenReady(testRepoService.update("user1", newUserModel)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to update user"))
      }
    }
  }

  "updateWithValue" should {
    "return an UpdateResult" in {
      (mockRepoTrait.updateWithValue(_: String, _: String, _: String))
        .expects(*, *, *)
        .returning(Future(Right(testUpdateResult)))
        .once()

      whenReady(testRepoService.updateWithValue("user1", "numFollowers", "1")) { result =>
        result shouldBe Right(testUpdateResult)
      }
    }

    "return an error" in {
      (mockRepoTrait.updateWithValue(_: String, _: String, _: String))
        .expects(*, *, *)
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Invalid field to update"))))
        .once()

      whenReady(testRepoService.updateWithValue("user1", "followers", "1")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Invalid field to update"))
      }
    }
  }

  "delete" should {
    "return a DeleteResult" in {
      (mockRepoTrait.delete(_: String))
        .expects(*)
        .returning(Future(Right(testDeleteResult)))
        .once()

      whenReady(testRepoService.delete("user1")) { result =>
        result shouldBe Right(testDeleteResult)
      }
    }

    "return an error" in {
      (mockRepoTrait.delete(_: String))
        .expects(*)
        .returning(Future(Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to delete user"))))
        .once()

      whenReady(testRepoService.delete("abcd")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Bad response from upstream; got status: 500, and got reason: Unable to delete user"))
      }
    }
  }
}
