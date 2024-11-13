package repositories

import com.google.inject.ImplementedBy
import models.{APIError, UserModel}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.empty
import org.mongodb.scala.model._
import org.mongodb.scala.result
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

  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]]  = {
    try {
      collection.find().toFuture().map{ users: Seq[UserModel] => Right(users) }
    }
    catch {
      case e: Exception => Future(Left(APIError.BadAPIResponse(404, "Database not found")))
    }
  }

  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]] = {
    try {
      collection.insertOne(user).toFuture()
        .map(_ => Right(user))
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

  // retrieve a DataModel object from database - uses an id parameter
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

  // remove all data from Mongo with the same collection name
  def deleteAll(): Future[Unit] = collection.deleteMany(empty()).toFuture().map(_ => ()) // needed for tests
}

@ImplementedBy(classOf[DataRepository])
trait DataRepositoryTrait {
  def index(): Future[Either[APIError.BadAPIResponse, Seq[UserModel]]]
  def create(user: UserModel): Future[Either[APIError.BadAPIResponse, UserModel]]
  def read(id: String): Future[Either[APIError, UserModel]]
//  def readBySpecifiedField(field: String, value: String): Future[Either[APIError, Seq[DataModel]]]
//  def update(id: String, book: DataModel): Future[Either[APIError, result.UpdateResult]]
//  def updateWithValue(id: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]]
//  def delete(id: String): Future[Either[APIError, result.DeleteResult]]
}