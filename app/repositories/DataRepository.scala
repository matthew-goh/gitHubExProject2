package repositories

import com.google.inject.ImplementedBy
import models.{APIError, UserModel}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.empty
import org.mongodb.scala.model._
import org.mongodb.scala.{DuplicateKeyException, result}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

  // TODO: change to Try
  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]]  = {
    try {
      collection.find().toFuture().map{ users: Seq[UserModel] => Right(users) }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(404, "Database collection not found")))
    }
  }

  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    try {
      collection.insertOne(user).toFuture()
        .map{ createdResult =>
          if (createdResult.wasAcknowledged) Right(user)
          else Left(APIError.BadAPIResponse(500, "Error: Insertion not acknowledged"))
        }
        .recover { case _ => Left(APIError.BadAPIResponse(500, "User already exists in database")) }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to add user")))
    }
  }

  private def byUsername(username: String): Bson =
    Filters.and(
      Filters.equal("username", username)
    )

//  private def bySpecifiedField(field: String, value: String): Bson =
//    Filters.and(
//      //      Filters.equal(field, value)
//      Filters.regex(field, s".*${value}.*", "i") // case-insensitive regex filter, containing search value
//    )

  def read(username: String): Future[Either[APIError, UserModel]] = {
    try {
      collection.find(byUsername(username)).headOption flatMap {
        case Some(data) => Future(Right(data))
        case None => Future(Left(APIError.BadAPIResponse(404, "User not found")))
      }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to search for user")))
    }
  }

  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]] = {
    try {
      collection.replaceOne(
        filter = byUsername(username),
        replacement = user,
        options = new ReplaceOptions().upsert(false) // don't add to database if user doesn't exist
      ).toFuture().map {
        updateResult =>
          if (updateResult.wasAcknowledged) {
            updateResult.getMatchedCount match {
              case 1 => Right(updateResult)
              case 0 => Left(APIError.BadAPIResponse(404, "User not found"))
              case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple documents with same username found"))
            }
          } else {
            Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
          }
      }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to update user")))
    }
  }
  // updateResult is e.g. AcknowledgedUpdateResult{matchedCount=1, modifiedCount=1, upsertedId=null}

  private def isIntegerString(value: String): Boolean = value.forall(Character.isDigit)

  def updateWithValue(username: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]] = {
    field match {
      case "location" =>
        try {
          collection.updateOne(Filters.equal("username", username), Updates.set(field, newValue)).toFuture().map{
            updateResult =>
              if (updateResult.wasAcknowledged) {
                updateResult.getMatchedCount match {
                case 1 => Right(updateResult)
                case 0 => Left(APIError.BadAPIResponse(404, "User not found"))
                case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users with same username found"))
              }
              } else {
                Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
              }
          }
        }
        catch {
          case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to update user")))
        }
      case "numFollowers" | "numFollowing" =>
        if(isIntegerString(newValue)) {
          try {
            collection.updateOne(Filters.equal("username", username), Updates.set(field, newValue.toInt)).toFuture().map{
              updateResult =>
                if (updateResult.wasAcknowledged) {
                  updateResult.getMatchedCount match {
                    case 1 => Right(updateResult)
                    case 0 => Left(APIError.BadAPIResponse(404, "User not found"))
                    case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users with same username found"))
                  }
                } else {
                  Left(APIError.BadAPIResponse(500, "Error: Update not acknowledged"))
                }
            }
          }
          catch {
            case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to update user")))
          }
        } else { // isIntegerString(newValue) == false
          Future(Left(APIError.BadAPIResponse(500, "New value must be an integer")))
        }
      case _ => Future(Left(APIError.BadAPIResponse(500, "Invalid field to update")))
    }
  }

  def delete(username: String): Future[Either[APIError, result.DeleteResult]] = {
    try {
      collection.deleteOne(
        filter = byUsername(username)
      ).toFuture().map { deleteResult =>
        if (deleteResult.wasAcknowledged) {
          deleteResult.getDeletedCount match {
            case 1 => Right(deleteResult)
            case 0 => Left(APIError.BadAPIResponse(404, "User not found"))
            case _ => Left(APIError.BadAPIResponse(500, "Error: Multiple users deleted"))
          }
        } else {
          Left(APIError.BadAPIResponse(500, "Error: Delete not acknowledged"))
        }
      }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(500, "Unable to delete user")))
    }
  }

  // remove all data from Mongo with the same collection name
  def deleteAll(): Future[Unit] = collection.deleteMany(empty()).toFuture().map(_ => ()) // needed for tests
}

@ImplementedBy(classOf[DataRepository])
trait DataRepositoryTrait {
  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]]
  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]]
  def read(username: String): Future[Either[APIError, UserModel]]
//  def readBySpecifiedField(field: String, value: String): Future[Either[APIError, Seq[DataModel]]]
  def update(username: String, user: UserModel): Future[Either[APIError, result.UpdateResult]]
  def updateWithValue(username: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]]
  def delete(username: String): Future[Either[APIError, result.DeleteResult]]
}