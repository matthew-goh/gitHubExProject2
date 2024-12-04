package services

import baseSpec.BaseSpec
import com.mongodb.client.result._
import models.{APIError, UserModel}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import repositories.{DataRepositoryTrait, UserModelFields}

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
        .returning(Future(Left(APIError.BadAPIResponse(500, "Unable to find database collection"))))
        .once()

      whenReady(testRepoService.index()) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Unable to find database collection"))
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
        .returning(Future(Left(APIError.BadAPIResponse(500, "User already exists in database"))))
        .once()

      whenReady(testRepoService.create(userModel)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "User already exists in database"))
      }
    }
  }

  "create (version called by ApplicationController addUser())" should {
    "return a DataModel" in {
      val reqBody = Some(Map(
        "username" -> List("user1"),
        "location" -> List(),
        "accountCreatedTime" -> List("2024-10-28T15:22:40Z"),
        "numFollowers" -> List("0"),
        "numFollowing" -> List("2")
      ))

      (mockRepoTrait.create(_: UserModel))
        .expects(userModel)
        .returning(Future(Right(userModel)))
        .once()

      whenReady(testRepoService.create(reqBody)) { result =>
        result shouldBe Right(userModel)
      }
    }

    "return an error from DataRepository" in {
      val reqBody = Some(Map(
        "username" -> List("user1"),
        "location" -> List(),
        "accountCreatedTime" -> List("2024-10-28T15:22:40Z"),
        "numFollowers" -> List("0"),
        "numFollowing" -> List("2")
      ))

      (mockRepoTrait.create(_: UserModel))
        .expects(*)
        .returning(Future(Left(APIError.BadAPIResponse(500, "User already exists in database"))))
        .once()

      whenReady(testRepoService.create(reqBody)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "User already exists in database"))
      }
    }

    "return an error if a required value is missing" in {
      val reqBody = Some(Map(
        "username" -> List(),
        "location" -> List(),
        "accountCreatedTime" -> List("2024-10-28T15:22:40Z"),
        "numFollowers" -> List("0"),
        "numFollowing" -> List("2")
      ))

      whenReady(testRepoService.create(reqBody)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(400, "Missing required value"))
      }
    }

    "return an error if an incorrect data type is provided" in {
      val reqBody = Some(Map(
        "username" -> List("user1"),
        "location" -> List(),
        "accountCreatedTime" -> List("2024-10-28T15:22:40Z"),
        "numFollowers" -> List("0"),
        "numFollowing" -> List("xyz")
      ))

      whenReady(testRepoService.create(reqBody)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(400, "Invalid data type"))
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
        .returning(Future(Left(APIError.BadAPIResponse(404, "User not found"))))
        .once()

      whenReady(testRepoService.read("abcd")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "User not found"))
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
        .returning(Future(Left(APIError.BadAPIResponse(500, "Unable to update user"))))
        .once()

      whenReady(testRepoService.update("user1", newUserModel)) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Unable to update user"))
      }
    }
  }

  "updateWithValue" should {
    "return an UpdateResult" in {
      (mockRepoTrait.updateWithValue(_: String, _: UserModelFields.Value, _: String))
        .expects("user1", UserModelFields.numFollowers, "1")
        .returning(Future(Right(testUpdateResult)))
        .once()

      whenReady(testRepoService.updateWithValue("user1", "numFollowers", "1")) { result =>
        result shouldBe Right(testUpdateResult)
      }
    }

    "return an error from DataRepository" in {
      (mockRepoTrait.updateWithValue(_: String, _: UserModelFields.Value, _: String))
        .expects("user1", UserModelFields.numFollowing, "xyz")
        .returning(Future(Left(APIError.BadAPIResponse(400, "New value must be an integer"))))
        .once()

      whenReady(testRepoService.updateWithValue("user1", "numFollowing", "xyz")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(400, "New value must be an integer"))
      }
    }

    "return an error if an invalid field is provided" in {
      whenReady(testRepoService.updateWithValue("user1", "followers", "1")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(400, "Invalid field to update"))
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
        .returning(Future(Left(APIError.BadAPIResponse(500, "Unable to delete user"))))
        .once()

      whenReady(testRepoService.delete("abcd")) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Unable to delete user"))
      }
    }
  }

  "deleteAll (test-only method)" should {
    "return a DeleteResult" in {
      (mockRepoTrait.deleteAll _)
        .expects()
        .returning(Future(Right(testDeleteResult)))
        .once()

      whenReady(testRepoService.deleteAll()) { result =>
        result shouldBe Right(testDeleteResult)
      }
    }

    "return an error" in {
      (mockRepoTrait.deleteAll _)
        .expects()
        .returning(Future(Left(APIError.BadAPIResponse(500, "Unable to delete all users"))))
        .once()

      whenReady(testRepoService.deleteAll()) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Unable to delete all users"))
      }
    }
  }
}
