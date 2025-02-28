/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.cost

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.StubBehaviour

import scala.concurrent.Future

class CostControllerSpec
  extends AnyWordSpec
     with Matchers
     with FakeApplicationBuilder
     with MockitoSugar
     with OptionValues {

  private val mockAuthStubBehaviour             = mock[StubBehaviour]
  private val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
  private val mockServiceConfigConnector        = mock[ServiceConfigsConnector]

  override def guiceApplicationBuilder: GuiceApplicationBuilder =
    super.guiceApplicationBuilder
      .overrides(
        bind[ServiceConfigsConnector].to(mockServiceConfigConnector),
        bind[TeamsAndRepositoriesConnector].to(mockTeamsAndRepositoriesConnector)
      )

  "must return OK and the correct view for a GET" in {
    when(mockAuthStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
      .thenReturn(Future.unit)

    when(mockTeamsAndRepositoriesConnector.allTeams(any)(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Seq.empty))

    when(mockTeamsAndRepositoriesConnector.allDigitalServices()(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Seq.empty))

    when(mockServiceConfigConnector.deploymentConfig(any, any, any, any, any)(using any[HeaderCarrier]))
      .thenReturn(Future.successful(Seq.empty))

    val request = FakeRequest(GET, routes.CostController.costExplorer().url)

    val result = route(app, request).value

    status(result) shouldBe 200
  }
}
