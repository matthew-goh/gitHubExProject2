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
    Indexes.ascending("_username")
  )), // can ensure the username to be unique
  replaceIndexes = false
) with DataRepositoryTrait {

}

@ImplementedBy(classOf[DataRepository])
trait DataRepositoryTrait {
//  def index(): Future[Either[APIError.BadAPIResponse, Seq[DataModel]]]
//  def create(book: DataModel): Future[Either[APIError.BadAPIResponse, DataModel]]
//  def read(id: String): Future[Either[APIError, DataModel]]
//  def readBySpecifiedField(field: String, value: String): Future[Either[APIError, Seq[DataModel]]]
//  def update(id: String, book: DataModel): Future[Either[APIError, result.UpdateResult]]
//  def updateWithValue(id: String, field: String, newValue: String): Future[Either[APIError, result.UpdateResult]]
//  def delete(id: String): Future[Either[APIError, result.DeleteResult]]
}