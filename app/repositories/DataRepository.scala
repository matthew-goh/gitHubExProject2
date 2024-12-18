package repositories

import com.google.inject.ImplementedBy
import models.{APIError, UserModel}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.empty
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoWriteException, result}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataRepository @Inject()(mongoComponent: MongoComponent)
                              (implicit ec: ExecutionContext) extends PlayMongoRepository[UserModel](
  // required parameters for the PlayMongoRepository abstract class
  collectionName = "users",
  mongoComponent = mongoComponent,
  domainFormat = UserModel.formats, // uses the implicit val formats in the UserModel object
  //-tells the driver how to read and write between a DataModel and JSON
  indexes = Seq(IndexModel(
    Indexes.ascending("username"),
    IndexOptions().unique(true)  // Ensures the index is unique
  )),
  replaceIndexes = false
) with DataRepositoryTrait {

  // Note: Try is meant for synchronous code.
  // For async operations (Future), use .recover or .recoverWith to handle exceptions.
  // The resulting value of a future is wrapped in either a Success or Failure type, which is a type of Try.

  def index(): Future[Either[APIError, Seq[UserModel]]] = {
    collection.find().toFuture().map{ users: Seq[UserModel] => Right(users) }
      .recover{
        case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to search database collection: ${e.getMessage}"))
      }
  }

  def create(user: UserModel): Future[Either[APIError, UserModel]] = {
    collection.insertOne(user).toFuture().map { insertResult =>
      if (insertResult.wasAcknowledged) {
        Right(user)
      } else {
        Left(APIError.BadAPIResponse(500, "Error: Insertion not acknowledged"))
      }
    }.recover {
      case e: MongoWriteException => Left(APIError.BadAPIResponse(500, "User already exists in database"))
      case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to add user: ${e.getMessage}"))
    }
  }

  private def byUsername(username: String): Bson =
    Filters.and(
      Filters.equal("username", username)
    )

  def read(username: String): Future[Either[APIError, UserModel]] = {
    collection.find(byUsername(username)).headOption.flatMap {
      case Some(data) => Future(Right(data))
      case None => Future(Left(APIError.BadAPIResponse(404, "User not found in database")))
    }.recover {
      case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to search for user: ${e.getMessage}"))
    }
  }

  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]] = {
    collection.replaceOne(
      filter = byUsername(username),
      replacement = user,
      options = new ReplaceOptions().upsert(false) // don't add to database if user doesn't exist
    ).toFuture().map {
      updateResult =>
        if (updateResult.wasAcknowledged) {
          updateResult.getMatchedCount match {
            case 1 => Right(updateResult)
            case 0 => Left(APIError.BadAPIResponse(404, "User not found in database"))
            case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users with same username found"))
          }
        } else {
          Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
        }
    }.recover {
      case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to update user: ${e.getMessage}"))
    }
  }
  // updateResult is e.g. AcknowledgedUpdateResult{matchedCount=1, modifiedCount=1, upsertedId=null}

  private def isIntegerString(value: String): Boolean = value.forall(Character.isDigit)

  def updateWithValue(username: String, field: UserModelFields.Value, newValue: String): Future[Either[APIError, result.UpdateResult]] = {
    field match {
      case UserModelFields.location =>
        collection.updateOne(Filters.equal("username", username), Updates.set("location", newValue)).toFuture().map{
          updateResult =>
            if (updateResult.wasAcknowledged) {
              updateResult.getMatchedCount match {
                case 1 => Right(updateResult)
                case 0 => Left(APIError.BadAPIResponse(404, "User not found in database"))
                case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users with same username found"))
              }
            } else {
              Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
            }
        }.recover {
          case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to update user: ${e.getMessage}"))
        }
      case UserModelFields.numFollowers | UserModelFields.numFollowing =>
        if(isIntegerString(newValue)) {
          collection.updateOne(Filters.equal("username", username), Updates.set(field.toString, newValue.toInt)).toFuture().map{
            updateResult =>
              if (updateResult.wasAcknowledged) {
                updateResult.getMatchedCount match {
                  case 1 => Right(updateResult)
                  case 0 => Left(APIError.BadAPIResponse(404, "User not found in database"))
                  case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users with same username found"))
                }
              } else {
                Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
              }
          }.recover {
            case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to update user: ${e.getMessage}"))
          }
        } else { // isIntegerString(newValue) == false
          Future.successful(Left(APIError.BadAPIResponse(400, "New value must be an integer")))
        }
    }
  }

  def delete(username: String): Future[Either[APIError, result.DeleteResult]] = {
    collection.deleteOne(
      filter = byUsername(username)
    ).toFuture().map { deleteResult =>
      if (deleteResult.wasAcknowledged) {
        deleteResult.getDeletedCount match {
          case 1 => Right(deleteResult)
          case 0 => Left(APIError.BadAPIResponse(404, "User not found in database"))
          case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users deleted"))
        }
      } else {
        Left(APIError.BadAPIResponse(500, "Error: Delete not acknowledged"))
      }
    }.recover {
      case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to delete user: ${e.getMessage}"))
    }
  }

  // remove all data from Mongo with the same collection name
  def deleteAll(): Future[Either[APIError, result.DeleteResult]] = {
    collection.deleteMany(empty()).toFuture().map{ deleteResult =>
      if (deleteResult.wasAcknowledged) Right(deleteResult)
      else Left(APIError.BadAPIResponse(500, "Error: Delete not acknowledged"))
    }.recover {
      case e: Throwable => Left(APIError.BadAPIResponse(500, s"Unable to delete all users: ${e.getMessage}"))
    }
  }
  def deleteAllForTesting(): Future[Unit] = collection.deleteMany(empty()).toFuture().map(_ => ()) // needed for tests
}

@ImplementedBy(classOf[DataRepository])
trait DataRepositoryTrait {
  def index(): Future[Either[APIError, Seq[UserModel]]]
  def create(user: UserModel): Future[Either[APIError, UserModel]]
  def read(username: String): Future[Either[APIError, UserModel]]
  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]]
  def updateWithValue(username: String, field: UserModelFields.Value, newValue: String): Future[Either[APIError, result.UpdateResult]]
  def delete(username: String): Future[Either[APIError, result.DeleteResult]]
  def deleteAll(): Future[Either[APIError, result.DeleteResult]]
}

object UserModelFields extends Enumeration {
  val location, numFollowers, numFollowing = Value
}
