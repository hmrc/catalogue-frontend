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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere._
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html.DeploymentHistoryPage

import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

class DeploymentHistoryControllerSpec extends UnitSpec with MockitoSugar with FakeApplicationBuilder {
  import ExecutionContext.Implicits.global

  private trait Fixture {

    lazy val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    lazy val mockedReleasesConnector             = mock[ReleasesConnector]
    lazy val page                                = new DeploymentHistoryPage()

    lazy val controller = new DeploymentHistoryController(
      mockedReleasesConnector,
      mockedTeamsAndRepositoriesConnector,
      page,
      Configuration.empty,
      stubMessagesControllerComponents()
    )
  }

  "history" should {

    "return 400 when given a bad date" in new Fixture {
      val response = controller.history()(FakeRequest(GET, "/deployments/production?from=baddate"))
      status(response) shouldBe 400
    }

    "return 200 when given no filters" in new Fixture {
      when(mockedReleasesConnector.deploymentHistory(any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(history = Seq.empty, 0)))
      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))
      val response = controller.history()(FakeRequest(GET, "/deployments/production"))
      status(response) shouldBe 200
    }

    "filter out audit records that do not fit the date range" in new Fixture {
      import DateHelper._

      val d1 = Instant.ofEpochMilli(LocalDate.parse("2020-01-01").atStartOfDayEpochMillis)
      val d2 = Instant.ofEpochMilli(LocalDate.parse("2020-02-01").atStartOfDayEpochMillis)
      val d3 = Instant.ofEpochMilli(LocalDate.parse("2020-03-01").atStartOfDayEpochMillis)

      val deps = Seq(
        DeploymentHistory(
          ServiceName("s1"),
          Environment.Production,
          VersionNumber("1.1.1"),
          Seq.empty,
          TimeSeen(d1),
          Username("usera")
        )
      )

      when(mockedReleasesConnector.deploymentHistory(any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(deps, deps.length)))
      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))
      val response = controller.history()(FakeRequest(GET, "/deployments/production?from=2020-01-01&to=2020-02-01"))
      status(response) shouldBe 200

      val responseString = contentAsString(response)
      responseString.contains("usera") shouldBe true
    }

    "request paginated data based on the page number" in new Fixture {
      when(
        mockedReleasesConnector
          .deploymentHistory(
            any(),
            any(),
            any(),
            any(),
            any(),
            eqTo(Some(2 * DeploymentHistoryController.pageSize)),
            eqTo(Some(DeploymentHistoryController.pageSize))
          )(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(Seq.empty, 0)))

      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.history()(FakeRequest(GET, "/deployments/production?page=2"))
      status(response) shouldBe 200
    }
  }
}
