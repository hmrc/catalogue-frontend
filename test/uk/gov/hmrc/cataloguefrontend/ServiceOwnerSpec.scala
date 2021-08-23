/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.test.Helpers
import uk.gov.hmrc.cataloguefrontend.actions.ActionsSupport
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService, ServiceOwnerSaveEventData, ServiceOwnerUpdatedEventData}
import uk.gov.hmrc.cataloguefrontend.service._
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterService
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html._

import scala.concurrent.{ExecutionContext, Future}

class ServiceOwnerSpec extends UnitSpec with MockitoSugar with ActionsSupport {
  import ExecutionContext.Implicits.global

  "serviceOwner" should {

    val digitalServiceName = "SomeDigitalService"
    val serviceOwner       = TeamMember(None, None, None, None, None, None)

    "return the service owner for a given digital service" in new Setup {
      when(mockedModelService.getDigitalServiceOwner(any()))
        .thenReturn(Some(serviceOwner))

      val response = catalogueController.serviceOwner(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      contentAsJson(response).as[TeamMember] shouldBe serviceOwner
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }

    "return the 404 when no owner found for a given digital service" in new Setup {

      when(mockedModelService.getDigitalServiceOwner(any()))
        .thenReturn(None)
      val response = catalogueController.serviceOwner(digitalServiceName)(FakeRequest())

      status(response) shouldBe 404

      contentAsJson(response).as[String] shouldBe s"owner for $digitalServiceName not found"
      verify(mockedModelService).getDigitalServiceOwner(digitalServiceName)
    }
  }

  "saveServiceOwner" should {

    val teamMember1 = teamMember("member 1", "member.1")
    val teamMembers = Seq(teamMember1, teamMember("member 2", "member.2"), teamMember("member 3", "member.3"))

    "save the username of the service owner and return the his/her full DisplayableTeamMember object" in new Setup {
      when(mockedModelService.getAllUsers)
        .thenReturn(teamMembers)
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any()))
        .thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")

      val response = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(serviceOwnerSaveEventData)))

      status(response) shouldBe 200

      contentAsJson(response).as[DisplayableTeamMember] shouldBe DisplayableTeamMember(
        teamMember1.getDisplayName,
        isServiceOwner = false,
        s"$profileBaseUrl/${teamMember1.username.get}"
      )

      verify(mockedEventService).saveServiceOwnerUpdatedEvent(ServiceOwnerUpdatedEventData("service-abc", "member.1"))
    }

    "not save the service owner if it doesn't contain a username" in new Setup {
      val member = TeamMember(Some("member 1"), None, None, None, None, None)
      when(mockedModelService.getAllUsers)
        .thenReturn(Seq(member))
      when(mockedEventService.saveServiceOwnerUpdatedEvent(any()))
        .thenReturn(Future.successful(true))

      val serviceOwnerSaveEventData = ServiceOwnerSaveEventData("service-abc", "member 1")
      val response = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(serviceOwnerSaveEventData)))

      status(response)                   shouldBe 417
      contentAsJson(response).as[String] shouldBe s"Username was not set (by UMP) for $member!"
      verifyZeroInteractions(mockedEventService)
    }

    "not save the user if he/she is not a valid user (from UMP)" in new Setup {
      when(mockedModelService.getAllUsers)
        .thenReturn(teamMembers)

      val ownerUpdatedEventData = ServiceOwnerSaveEventData("service-abc", "Mrs Invalid Person")
      val response = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withJsonBody(Json.toJson(ownerUpdatedEventData)))

      status(response) shouldBe NOT_ACCEPTABLE

      contentAsJson(response).as[String] shouldBe s"Invalid user: ${ownerUpdatedEventData.displayName}"
      verifyZeroInteractions(mockedEventService)
    }

    "return a BadRequest error if the sent json is valid" in new Setup {
      when(mockedModelService.getAllUsers)
        .thenReturn(teamMembers)

      val response = catalogueController
        .saveServiceOwner()(
          FakeRequest(Helpers.POST, "/")
            .withHeaders("Content-Type" -> "application/json")
            .withTextBody("some invalid json"))

      status(response) shouldBe BAD_REQUEST

      contentAsJson(response).as[String] shouldBe s"""Unable to parse json: "some invalid json""""
      verifyZeroInteractions(mockedEventService)
    }
  }

  private def teamMember(displayName: String, userName: String) =
    TeamMember(Some(displayName), None, None, None, None, Some(userName))

  private trait Setup {
    val mockedModelService                 = mock[ReadModelService]
    val mockedEventService                 = mock[EventService]
    private val userManagementPortalConfig = mock[UserManagementPortalConfig]
    private val umac                       = mock[UserManagementAuthConnector]
    private val controllerComponents       = stubMessagesControllerComponents()
    private val catalogueErrorHandler      = mock[CatalogueErrorHandler]

    val profileBaseUrl = "http://things.things.com"
    when(userManagementPortalConfig.userManagementProfileBaseUrl)
      .thenReturn(profileBaseUrl)

    val catalogueController: CatalogueController = new CatalogueController(
      mock[UserManagementConnector],
      mock[TeamsAndRepositoriesConnector],
      mock[ConfigService],
      mock[RouteRulesService],
      mock[ServiceDependenciesConnector],
      mock[LeakDetectionService],
      mockedEventService,
      mockedModelService,
      mock[ShutterService],
      new VerifySignInStatusPassThrough(umac, controllerComponents),
      new UmpAuthenticatedPassThrough(umac, controllerComponents, catalogueErrorHandler),
      userManagementPortalConfig,
      Configuration.empty,
      controllerComponents,
      mock[DigitalServiceInfoPage],
      mock[WhatsRunningWhereService],
      mock[IndexPage],
      mock[TeamInfoPage],
      mock[ServiceInfoPage],
      mock[ServiceConfigPage],
      mock[ServiceConfigRawPage],
      mock[LibraryInfoPage],
      mock[PrototypeInfoPage],
      mock[RepositoryInfoPage],
      mock[RepositoriesListPage],
      mock[DefaultBranchListPage],
      mock[OutOfDateTeamDependenciesPage]
    )
  }
}
