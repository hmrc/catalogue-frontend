/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{DeploymentHistoryController, ReleasesConnector}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html.DeploymentHistoryPage

import scala.concurrent.{ExecutionContext, Future}

class DeploymentHistoryControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {
  import ExecutionContext.Implicits.global

  private trait Fixture {

    lazy val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    lazy val mockedReleasesConnector             = mock[ReleasesConnector]
    lazy val page = new DeploymentHistoryPage()

    lazy val controller = new DeploymentHistoryController(
      mockedReleasesConnector,
      mockedTeamsAndRepositoriesConnector,
      page,
      stubMessagesControllerComponents()
    )
  }

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )
      .build()

  "history" should {
    "return 400 when given a bad date" in new Fixture {
      val response = controller.history()(FakeRequest(GET, "/deployments/production?from=baddate"))
      status(response) shouldBe 400
    }
    "return 200 when given no filters" in new Fixture {
      when(mockedReleasesConnector.deploymentHistory(any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(Seq.empty))
      when(mockedTeamsAndRepositoriesConnector.allTeams(any())).thenReturn(Future.successful(Seq.empty))
      val response = controller.history()(FakeRequest(GET, "/deployments/production"))
      status(response) shouldBe 200
    }
  }
}
