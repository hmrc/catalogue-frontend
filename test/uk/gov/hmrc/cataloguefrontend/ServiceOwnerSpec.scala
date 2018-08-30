/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import play.test.Helpers
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.actions.ActionsSupport
import uk.gov.hmrc.cataloguefrontend.connector.{IndicatorsConnector, ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementAuthConnector}
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService, ServiceOwnerSaveEventData, ServiceOwnerUpdatedEventData}
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ServiceOwnerSpec
    extends UnitSpec
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with MockitoSugar
    with ScalaFutures
    with ActionsSupport {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.teams-and-services.host"      -> host,
      "microservice.services.teams-and-services.port"      -> endpointPort,
      "microservice.services.indicators.port"              -> endpointPort,
      "microservice.services.indicators.host"              -> host,
      "microservice.services.user-management.url"          -> endpointMockUrl,
      "usermanagement.portal.url"                          -> "http://usermanagement/link",
      "user-management.profileBaseUrl"                     -> "http://usermanagement/linkBase",
      "microservice.services.user-management.frontPageUrl" -> "http://some.ump.fontpage.com",
      "play.ws.ssl.loose.acceptAnyCertificate"             -> true,
      "play.http.requestHandler"                           -> "play.api.http.DefaultHttpRequestHandler"
    )
    .build()

  val serviceConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val umac = app.injector.instanceOf[UserManagementAuthConnector]
  val mcc = app.injector.instanceOf[MessagesControllerComponents]
  val verifySignInStatusPassThrough = new VerifySignInStatusPassThrough(umac, mcc)
  val umpAuthenticatedPassThrough = new UmpAuthenticatedPassThrough(umac, mcc)

  "serviceOwner" should {

    val digitalServiceName = "SomeDigitalService"
    val serviceOwner       = TeamMember(None, None, None, None, None, None)

    "return the service owner for a given digital service" in new Setup {

      when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(Some(serviceOwner))
      val response: Result = catalogueController.serviceOwner(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 200

      val responseAsJson = contentAsJson(response)
      responseAsJson.as[TeamMember] shouldBe serviceOwner
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }

    "return the 404 when no owner found for a given digital service" in new Setup {

      when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(None)
      val response: Result = catalogueController.serviceOwner(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 404

      val responseAsJson: JsValue = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"owner for $digitalServiceName not found"
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }
  }

  "saveServiceOwner" should {

    val teamMember1 = teamMember("member 1", "member.1")
    val teamMembers = Seq(teamMember1, teamMember("member 2", "member.2"), teamMember("member 3", "member.3"))

    "save the username of the service owner and return the his/her full DisplayableTeamMember object" in new Setup {
      when(mockedModelService.getAllUsers).thenReturn(teamMembers)
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any())).thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")
      val response: Result = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(serviceOwnerSaveEventData)))
        .futureValue

      verify(mockedEventService).saveServiceOwnerUpdatedEvent(ServiceOwnerUpdatedEventData("service-abc", "member.1"))

      response.header.status shouldBe 200

      contentAsJson(response).as[DisplayableTeamMember] shouldBe DisplayableTeamMember(
        teamMember1.getDisplayName,
        isServiceOwner = false,
        s"$umpBaseUrl/${teamMember1.username.get}")
    }

    "not save the service owner if it doesn't contain a username" in new Setup {
      val member = TeamMember(Some("member 1"), None, None, None, None, None)
      when(mockedModelService.getAllUsers).thenReturn(Seq(member))
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any())).thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")
      val response: Result = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(serviceOwnerSaveEventData)))
        .futureValue

      response.header.status shouldBe 417
      val responseAsJson: JsValue = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"Username was not set (by UMP) for $member!"
      Mockito.verifyZeroInteractions(mockedEventService)

    }

    "not save the user if he/she is not a valid user (from UMP)" in new Setup {
      when(mockedModelService.getAllUsers).thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerSaveEventData("service-abc", "Mrs Invalid Person")
      val response: Result = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(ownerUpdatedEventData)))
        .futureValue

      response.header.status shouldBe NOT_ACCEPTABLE

      val responseAsJson: JsValue = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"Invalid user: ${ownerUpdatedEventData.displayName}"
      Mockito.verifyZeroInteractions(mockedEventService)
    }

    "return a BadRequest error if the sent json is valid" in new Setup {
      when(mockedModelService.getAllUsers).thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerUpdatedEventData("service-abc", "Mrs Invalid Person")
      val response: Result = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withTextBody("some invalid json"))
        .futureValue

      response.header.status shouldBe BAD_REQUEST

      val responseAsJson: JsValue = contentAsJson(response)
      responseAsJson.as[String] shouldBe s"""Unable to parse json: "some invalid json""""
      Mockito.verifyZeroInteractions(mockedEventService)
    }
  }

  private def teamMember(displayName: String, userName: String) =
    TeamMember(Some(displayName), None, None, None, None, Some(userName))

  private trait Setup {
    val umpBaseUrl         = "http://things.things.com"
    val mockedModelService = mock[ReadModelService]
    val mockedEventService = mock[EventService]

    val customConf = new ServicesConfig(
      Configuration.empty,
      new RunMode(Configuration.empty, Mode.Test)
    ){
        override def getConfString(key: String, defString: => String): String =
          key match {
            case "user-management.profileBaseUrl" => umpBaseUrl
            case _                                => serviceConfig.getConfString(key, defString)
          }
    }

    val catalogueController: CatalogueController = new CatalogueController(
      mock[uk.gov.hmrc.cataloguefrontend.UserManagementConnector],
      mock[TeamsAndRepositoriesConnector],
      mock[ServiceDependenciesConnector],
      mock[IndicatorsConnector],
      mock[LeakDetectionService],
      mock[DeploymentsService],
      mockedEventService,
      mockedModelService,
      app.environment,
      verifySignInStatusPassThrough,
      umpAuthenticatedPassThrough,
      app.injector.instanceOf[ServicesConfig],
      app.injector.instanceOf[ViewMessages],
      app.injector.instanceOf[MessagesControllerComponents]
    ) {

      def getConfString(key: String, defString: => String): String =
        key match {
          case "user-management.profileBaseUrl" => umpBaseUrl
          case _                                => serviceConfig.getConfString(key, defString)
        }

    }
  }
}
