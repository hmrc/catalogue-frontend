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

package uk.gov.hmrc.cataloguefrontend.connector

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionConnectorSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  "repositoriesWithLeaks" should {
    "return empty if leak detection service returns status different than 2xx" in {
      implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
      val servicesConfig                        = mock[ServicesConfig]
      val httpClient                            = mock[HttpClient]

      when(servicesConfig.baseUrl(any()))
        .thenReturn("http://leak-detection:8855")

      when(httpClient.GET(any())(any(), any(), any()))
        .thenReturn(Future.failed(new BadGatewayException("an exception")))

      val leakDetectionConnector = new LeakDetectionConnector(httpClient, servicesConfig)

      leakDetectionConnector.repositoriesWithLeaks.futureValue shouldBe Seq.empty
    }
  }
}
