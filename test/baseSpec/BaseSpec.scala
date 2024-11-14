package baseSpec

import akka.stream.Materializer
import connectors.GithubConnector
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST}
import repositories.DataRepository
import services.{GithubService, RepositoryService}
import shared.TestRequest

import scala.concurrent.ExecutionContext

trait BaseSpec extends AnyWordSpec with Matchers

trait BaseSpecWithApplication extends BaseSpec with GuiceOneServerPerSuite with ScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with Eventually {

  implicit val mat: Materializer = app.materializer
  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  // created instances of the controller components and data repository - new instances will be made for each suite run
  lazy val component: MessagesControllerComponents = injector.instanceOf[MessagesControllerComponents]
  lazy val repoService: RepositoryService = injector.instanceOf[RepositoryService]
  lazy val repository: DataRepository = injector.instanceOf[DataRepository]
  lazy val service: GithubService = injector.instanceOf[GithubService]
  lazy val connector: GithubConnector = injector.instanceOf[GithubConnector]

  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  lazy val injector: Injector = app.injector

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(Map(
        "mongodb.uri"                                    -> "mongodb://localhost:27017/githubTutorial"
      ))
      .build()


  protected val testRequest: TestRequest = new TestRequest(messagesApi)

//  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
//    FakeRequest("", "").withCSRFToken.asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
//  implicit val messages: Messages = messagesApi.preferred(fakeRequest)
//
//  // methods we can use in the tests to pass fake requests to the controller
//  def buildPost(url: String): FakeRequest[AnyContentAsEmpty.type] =
//    FakeRequest(POST, url).withCSRFToken.asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
//
//  def buildGet(url: String): FakeRequest[AnyContentAsEmpty.type] =
//    FakeRequest(GET, url).withCSRFToken.asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
}