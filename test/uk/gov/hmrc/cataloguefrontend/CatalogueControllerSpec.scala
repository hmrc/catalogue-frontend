/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend

import akka.stream.Materializer
import akka.util.ByteString
import com.github.tomakehurst.wiremock.http.RequestMethod.{GET => WIREMOCK_GET}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.Accumulator
import play.api.libs.ws.WS
import play.api.mvc.{Action, Result}
import play.api.test.FakeRequest
import play.test.Helpers
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService, ServiceOwnerUpdatedEventData}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConversions._
import scala.collection.immutable
import scala.concurrent.Future
import scala.io.Source
import play.api.test.Helpers._

class CatalogueControllerSpec extends UnitSpec with BeforeAndAfterEach with OneServerPerSuite with WireMockEndpoints with MockitoSugar with ScalaFutures {

  val umpFrontPageUrl = "http://some.ump.fontpage.com"
  val umpBaseUrl = "http://things.things.com"


  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.user-management.url" -> endpointMockUrl,
    "usermanagement.portal.url" -> "http://usermanagement/link",
    "user-management.profileBaseUrl" -> "http://usermanagement/linkBase",
    "microservice.services.user-management.frontPageUrl" -> umpFrontPageUrl,
    "play.ws.ssl.loose.acceptAnyCertificate" -> true,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()

  implicit val materializer = app.injector.instanceOf[Materializer]


  val mockedModelService = mock[ReadModelService]
  val mockedEventService = mock[EventService]


  override def afterEach() {
    Mockito.reset(mockedModelService)
    Mockito.reset(mockedEventService)
  }

  val catalogueController = new CatalogueController {

    override def getConfString(key: String, defString: => String): String = {
      key match {
        case "user-management.profileBaseUrl" => umpBaseUrl
        case _ => super.getConfString(key, defString)
      }
    }

    override def userManagementConnector: UserManagementConnector = ???
    override def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = ???
    override def indicatorsConnector: IndicatorsConnector = ???
    override def deploymentsService: DeploymentsService = ???

    override def readModelService: ReadModelService = mockedModelService
    override def eventService: EventService = mockedEventService
  }


  "serviceOwner" should {

    val digitalServiceName = "SomeDigitalService"
    val serviceOwner = TeamMember(None,None,None,None,None,None)

    "return the service owner for a given digital service" in {

      when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(Some(serviceOwner))
      val response: Result = catalogueController.serviceOwner(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 200

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[TeamMember] shouldBe serviceOwner
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }
    
    "return the 404 when no owner found for a given digital service" in {

      when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(None)
      val response: Result = catalogueController.serviceOwner(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 404

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"owner for $digitalServiceName not found"
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }
  }


  "saveServiceOwner" should {

    val teamMember1 = teamMember("member 1", "member.1")
    val teamMembers = Seq(
      teamMember1,
      teamMember("member 2", "member.2"),
      teamMember("member 3", "member.3"))

    "save the username of the service owner and return the his/her full DisplayableTeamMember object" in {
      when(mockedModelService.getAllUsers).thenReturn(teamMembers)
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any())).thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.toJson(serviceOwnerSaveEventData))
      ).futureValue

      verify(mockedEventService).saveServiceOwnerUpdatedEvent(ServiceOwnerUpdatedEventData("service-abc", "member.1"))

      response.header.status shouldBe 200

      contentAsJson(response).as[DisplayableTeamMember] shouldBe DisplayableTeamMember(teamMember1.getDisplayName, false, s"$umpBaseUrl/${teamMember1.username.get}"  )
    }

    "not save the service owner if it doesn't contain a username" in {
      val member = TeamMember(Some("member 1"), None, None, None, None, None)
      when(mockedModelService.getAllUsers).thenReturn(Seq(member))
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any())).thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.toJson(serviceOwnerSaveEventData))
      ).futureValue

      response.header.status shouldBe 417
      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"Username was not set (by UMP) for $member!"
      Mockito.verifyZeroInteractions(mockedEventService)

    }

    "not save the user if he/she is not a valid user (from UMP)" in {

      when(mockedModelService.getAllUsers).thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerSaveEventData("service-abc", "Mrs Invalid Person")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
      .withHeaders("Content-Type" -> "application/json")
      .withJsonBody(Json.toJson(ownerUpdatedEventData))
      ).futureValue

      response.header.status shouldBe NOT_ACCEPTABLE

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"Invalid user: ${ownerUpdatedEventData.displayName}"
      Mockito.verifyZeroInteractions(mockedEventService)
    }

    "return a BadRequest error if the sent json is valid" in {

      when(mockedModelService.getAllUsers).thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerUpdatedEventData("service-abc", "Mrs Invalid Person")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
      .withHeaders("Content-Type" -> "application/json")
      .withTextBody("some invalid json")
      ).futureValue

      response.header.status shouldBe BAD_REQUEST

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe  s"""Unable to parse json: "some invalid json""""
      Mockito.verifyZeroInteractions(mockedEventService)
    }
  }


  private def teamMember(displayName: String, userName: String) = {
    TeamMember(Some(displayName), None, None, None, None, Some(userName))
  }

}
