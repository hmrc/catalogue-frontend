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

import java.time.LocalDateTime

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers.{eq => is, _}
import org.mockito.Mockito.when
import org.scalatest.Matchers._
import org.scalatest.{BeforeAndAfter, WordSpec}
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, DeploymentsService, LeakDetectionService, TeamRelease}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html._

import scala.concurrent.Future

class CatalogueControllerSpec extends WordSpec with MockitoSugar with BeforeAndAfter {

  "deploymentsList" should {

    "fetch deployments and return deployments list with found releases if deployments filter can be bound" in new Setup {
      val teamReleases = Seq(
        TeamRelease(
          name           = "service-name1",
          teams          = Seq("team-name1"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        ),
        TeamRelease(
          name           = "service-name2",
          teams          = Seq("team-name2"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        )
      )

      when(deploymentsService.getDeployments(is(None), is(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(teamReleases))

      val result = controller.deploymentsList(teamName = Some(""), serviceName = Some(""))(FakeRequest())

      status(result)                            shouldBe OK
      result.toDocument.select("#row0").isEmpty shouldBe false
      result.toDocument.select("#row1").isEmpty shouldBe false
    }

    "fetch deployments for certain team and return deployments list with found releases" in new Setup {
      val teamReleases = Seq(
        TeamRelease(
          name           = "service-name1",
          teams          = Seq("team-name1"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        )
      )

      when(deploymentsService.getDeployments(is(Some("team-name1")), is(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(teamReleases))

      val result = controller.deploymentsList(teamName = Some("team-name1"), serviceName = None)(FakeRequest())

      status(result)                            shouldBe OK
      result.toDocument.select("#row0").isEmpty shouldBe false
      result.toDocument.select("#row1").isEmpty shouldBe true
    }

    "fetch deployments for certain service and return deployments list with found releases" in new Setup {
      val teamReleases = Seq(
        TeamRelease(
          name           = "service-name1",
          teams          = Seq("team-name1"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        )
      )

      when(deploymentsService.getDeployments(is(None), is(Some("service-name1")))(any[HeaderCarrier]))
        .thenReturn(Future.successful(teamReleases))

      val result = controller.deploymentsList(teamName = None, serviceName = Some("service-name1"))(FakeRequest())

      status(result)                            shouldBe OK
      result.toDocument.select("#row0").isEmpty shouldBe false
      result.toDocument.select("#row1").isEmpty shouldBe true
    }

    "fetch deployments and return deployments list with no elements if deployments filter cannot be bound" in new Setup {
      val teamReleases = Seq(
        TeamRelease(
          name           = "service-name1",
          teams          = Seq("team-name1"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        ),
        TeamRelease(
          name           = "service-name2",
          teams          = Seq("team-name2"),
          productionDate = LocalDateTime.now(),
          version        = "1.0.0"
        )
      )

      when(deploymentsService.getDeployments(is(None), is(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(teamReleases))

      val requestWithInvalidForm = FakeRequest().withFormUrlEncodedBody("from" -> "abc")

      val result = controller.deploymentsList(teamName = None, serviceName = None)(requestWithInvalidForm)

      status(result)                            shouldBe OK
      result.toDocument.select("#row0").isEmpty shouldBe true
    }
  }


  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val deploymentsService         = mock[DeploymentsService]
    val userManagementPortalConfig = mock[UserManagementPortalConfig]

    when(userManagementPortalConfig.userManagementProfileBaseUrl).thenReturn("profile-base-url")

    val controller = new CatalogueController(
      mock[UserManagementConnector],
      mock[TeamsAndRepositoriesConnector],
      mock[ConfigService],
      mock[ServiceDependenciesConnector],
      mock[IndicatorsConnector],
      mock[LeakDetectionService],
      deploymentsService,
      mock[EventService],
      mock[ReadModelService],
      mock[VerifySignInStatus],
      mock[UmpAuthenticated],
      userManagementPortalConfig,
      stubMessagesControllerComponents(),
      mock[DigitalServiceInfoPage],
      mock[IndexPage],
      mock[TeamInfoPage],
      mock[ServiceInfoPage],
      mock[ServiceConfigPage],
      mock[ServiceConfigRawPage],
      mock[LibraryInfoPage],
      mock[PrototypeInfoPage],
      mock[RepositoryInfoPage],
      mock[RepositoriesListPage]
    )
  }

  private implicit class ResultOps(eventualResult: Future[Result]) {
    lazy val toDocument: Document = Jsoup.parse(contentAsString(eventualResult))
  }
}
