/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentMatcher
import org.mockito.Matchers.{any, argThat, eq => is}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.shuttering.Environment.QA
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

class ShutterConnectorSpec extends WordSpec with MockitoSugar with Matchers with ScalaFutures {

  import ShutterConnectorSpec._

  private trait Fixture {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("shutter-api")).thenReturn(SomeBaseUrl)

    implicit val headerCarrier = HeaderCarrier()
    implicit val executionContext = scala.concurrent.ExecutionContext.global
    val httpClient = mock[HttpClient]
    val underTest = new ShutterConnector(httpClient, servicesConfig)

    // unfortunately the test will receive an unhelpful NullPointerException if expectations are not met
    def stubEmptyResponseForGet(withPath: String, withParams: Seq[(String, String)]): Unit =
      when(httpClient.GET(argThat(new UrlArgumentMatcher(withPath, withParams)))(
        any[HttpReads[Seq[ShutterEvent]]], is(headerCarrier), is(executionContext))).thenReturn(Future.successful(Seq.empty))
  }

  "Shutter Events" should {
    "be filtered by environment only when no service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = QA, serviceName = None)
      stubEmptyResponseForGet(withPath = s"$SomeBaseUrl/shutter-api/events", withParams = Seq(
        "type" -> "shutter-state-change",
        "data.environment" -> "qa"
      ))

      underTest.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty
    }

    "be filtered by serviceName and environment when a service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = QA, serviceName = Some("abc-frontend"))
      stubEmptyResponseForGet(withPath = s"$SomeBaseUrl/shutter-api/events", withParams = Seq(
        "type" -> "shutter-state-change",
        "data.environment" -> "qa",
        "data.serviceName" -> "abc-frontend"
      ))

      underTest.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty
    }
  }
}

private object ShutterConnectorSpec {
  val SomeBaseUrl = "http://somebaseurl"

  class UrlArgumentMatcher(path: String, params: Seq[(String, String)]) extends ArgumentMatcher[String] {
    override def matches(arg: Any): Boolean =
      arg.isInstanceOf[String] && isMatchingUrl(arg.toString)

    private def isMatchingUrl(url: String): Boolean = {
      val indexOfQueryStringSeparator = url.indexOf('?')
      if (indexOfQueryStringSeparator < 0) {
        url == path && params.isEmpty
      } else {
        val urlPath = url.substring(0, indexOfQueryStringSeparator)
        val urlQueryParams = url.substring(indexOfQueryStringSeparator + 1).split('&').toList.map(toKeyValue)
        path == urlPath && params.sorted == urlQueryParams.sorted
      }
    }

    private def toKeyValue(query: String): (String, String) = {
      val indexOfKeyValueSeparator = query.indexOf('=')
      if (indexOfKeyValueSeparator < 0) query -> ""
      else query.substring(0, indexOfKeyValueSeparator) -> query.substring(indexOfKeyValueSeparator + 1)
    }
  }
}