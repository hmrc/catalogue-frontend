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

package uk.gov.hmrc.cataloguefrontend.shuttering

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class ShutterConnectorSpec extends AnyWordSpec with MockitoSugar with Matchers with ScalaFutures {

  import ShutterConnectorSpec._

  private trait Fixture {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("shutter-api")).thenReturn(SomeBaseUrl)

    implicit val headerCarrier = HeaderCarrier()
    implicit val executionContext = scala.concurrent.ExecutionContext.global
    val httpClient = mock[HttpClient]
    val underTest = new ShutterConnector(httpClient, servicesConfig)

    // unfortunately the test will receive an unhelpful NullPointerException if expectations are not met
    def stubEmptyResponseForGet(
      withPath   : String,
      withParams : Seq[(String, String)]
    ): Unit =
      when(
        httpClient.GET(
          eqTo(url"$withPath?$withParams")
        )(any[HttpReads[Seq[ShutterEvent]]],
          eqTo(headerCarrier),
          eqTo(executionContext)
        )
      ).thenReturn(Future.successful(Seq.empty))
  }

  "Shutter Events" should {
    "be filtered by environment only when no service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.QA, serviceName = None)
      stubEmptyResponseForGet(
        withPath   = s"$SomeBaseUrl/shutter-api/events",
        withParams = Seq(
          "type"             -> "shutter-state-change",
          "data.environment" -> "qa"
        )
      )

      underTest.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty
    }

    "be filtered by serviceName and environment when a service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.QA, serviceName = Some("abc-frontend"))
      stubEmptyResponseForGet(
        withPath   = s"$SomeBaseUrl/shutter-api/events",
        withParams = Seq(
          "type"             -> "shutter-state-change",
          "data.environment" -> "qa",
          "data.serviceName" -> "abc-frontend"
        )
      )

      underTest.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty
    }
  }
}

private object ShutterConnectorSpec {
  val SomeBaseUrl = "http://somebaseurl"
}
