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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.{DateHelper, FakeApplicationBuilder}
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
      when(mockedReleasesConnector.deploymentHistory(environment = any(), from = any(), to = any(), team = any(), service = any(), skip = any(), limit = any())(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(history = Seq.empty, 0)))
      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))
      val response = controller.history()(FakeRequest(GET, "/deployments/production"))
      status(response) shouldBe 200
    }

    "request the api filter out audit records by date" in new Fixture {
      import DateHelper._

      val d1 = Instant.ofEpochMilli(LocalDate.parse("2020-01-01").atStartOfDayEpochMillis)

      private val deps = Seq(
        DeploymentHistory(
          ServiceName("s1"),
          Environment.Production,
          VersionNumber("1.1.1"),
          Seq.empty,
          TimeSeen(d1),
          Username("user_a")
        )
      )

      when(mockedReleasesConnector.deploymentHistory(environment = any(), from = any(), to = any(), team = any(), service = any(), skip = any(), limit = any())(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(deps, deps.length)))
      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.history()(FakeRequest(GET, "/deployments/production?from=2020-01-01&to=2020-02-01"))
      status(response) shouldBe 200

      val responseString = contentAsString(response)
      responseString.contains("user_a") shouldBe true
    }

    "request api filter by service name" in new Fixture {
      import DateHelper._

      val d1 = Instant.ofEpochMilli(LocalDate.parse("2020-01-01").atStartOfDayEpochMillis)

      val deps = Seq(
        DeploymentHistory(
          ServiceName("s1"),
          Environment.Production,
          VersionNumber("1.1.1"),
          Seq.empty,
          TimeSeen(d1),
          Username("user_a")
        )
      )

      when(
        mockedReleasesConnector
          .deploymentHistory(environment = eqTo(Environment.Production), from = any(), to = any(), team = eqTo(None), service = eqTo(Some("s1")), skip = eqTo(None), limit = any())(
            any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(deps, deps.length)))

      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.history()(FakeRequest(GET, "/deployments/production?service=s1"))
      status(response) shouldBe 200

      val responseString = contentAsString(response)
      responseString.contains("user_a") shouldBe true
    }

    "request paginated data based on the page number" in new Fixture {
      when(
        mockedReleasesConnector
          .deploymentHistory(
            eqTo(Environment.Production),
            from    = any(),
            to      = any(),
            team    = eqTo(None),
            service = eqTo(None),
            skip    = eqTo(Some(2 * DeploymentHistoryController.pageSize)),
            limit   = eqTo(Some(DeploymentHistoryController.pageSize))
          )(any()))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(Seq.empty, 0)))

      when(mockedTeamsAndRepositoriesConnector.allTeams(any()))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.history()(FakeRequest(GET, "/deployments/production?page=2"))
      status(response) shouldBe 200
    }
  }
}
