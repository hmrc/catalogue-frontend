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

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.user-management.url" -> endpointMockUrl,
    "usermanagement.portal.url" -> "http://usermanagement/link",
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
    override def userManagementConnector: UserManagementConnector = ???
    override def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = ???
    override def indicatorsConnector: IndicatorsConnector = ???
    override def deploymentsService: DeploymentsService = ???

    override def readModelService: ReadModelService = mockedModelService
    override def eventService: EventService = mockedEventService
  }


  "serviceOwner" should {

    val digitalServiceName = "SomeDigitalService"
    val serviceOwnerName = "Some Owner"

    "return the service owner for a given digital service" in {


      when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(Some(serviceOwnerName))
      val response: Result = catalogueController.serviceOwner(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 200

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe serviceOwnerName
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

    val teamMembers = Seq(teamMember("member 1"), teamMember("member 2"), teamMember("member 3"))

    "delegate the saving of the service owner to the EventService" in {
      when(mockedModelService.getAllUsers).thenReturn(teamMembers)
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any())).thenReturn(Future.successful(true))

      val ownerUpdatedEventData = ServiceOwnerUpdatedEventData("service-abc", "member 1")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
      .withHeaders("Content-Type" -> "application/json")
      .withJsonBody(Json.toJson(ownerUpdatedEventData))
      ).futureValue

      response.header.status shouldBe 200

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe "member 1"
      verify(mockedEventService).saveServiceOwnerUpdatedEvent(ownerUpdatedEventData)
    }

    "not save the user if he/she is not a valid user (from UMP)" in {

      when(mockedModelService.getAllUsers).thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerUpdatedEventData("service-abc", "Mrs Invalid Person")
      val response = catalogueController.saveServiceOwner()(FakeRequest(Helpers.POST, "/")
      .withHeaders("Content-Type" -> "application/json")
      .withJsonBody(Json.toJson(ownerUpdatedEventData))
      ).futureValue

      response.header.status shouldBe NOT_ACCEPTABLE

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"Invalid user: ${ownerUpdatedEventData.name}"
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


  private def teamMember(displayName: String) = {
    TeamMember(Some(displayName), None, None, None, None, None)
  }

}
