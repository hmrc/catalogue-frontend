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

import java.time.Instant

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Matchers.{any, eq => is}
import org.mockito.Mockito.when
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Helpers}
import uk.gov.hmrc.cataloguefrontend.shuttering.Environment.{Development, Production}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterCause.UserCreated
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterStatus.Unshuttered
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType.Frontend
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShutterEventsControllerSpec extends WordSpec with MockitoSugar with Matchers with GuiceOneAppPerSuite with
  DefaultAwaitTimeout with OptionValues {

  import Helpers._
  import ShutterEventsControllerSpec._

  private trait Fixture {
    val mcc = app.injector.instanceOf[MessagesControllerComponents]
    val connector = mock[ShutterConnector]
    val underTest = new ShutterEventsController(mcc, connector)

    def stubConnectorSuccess(forFilter: ShutterEventsFilter, returnEvents: Seq[ShutterStateChangeEvent] = Seq.empty): Unit =
      when(connector.shutterEventsByTimestampDesc(is(forFilter))(any[HttpReads[Seq[ShutterEvent]]], any[HeaderCarrier])).thenReturn(
        Future.successful(returnEvents)
      )

    def stubConnectorFailure(forFilter: ShutterEventsFilter): Unit =
      when(connector.shutterEventsByTimestampDesc(is(forFilter))(any[HttpReads[Seq[ShutterEvent]]], any[HeaderCarrier])).thenReturn(
        Future.failed(new RuntimeException("connector failure"))
      )

    implicit class ResultOps(eventualResult: Future[Result]) {
      lazy val toDocument: Document = Jsoup.parse(contentAsString(eventualResult))
    }
  }

  "Shutter Events Controller" should {
    "redirect from the shutter events entry point to the production shutter events list" in new Fixture {
      val futResult = underTest.shutterEvents(FakeRequest())

      redirectLocation(futResult).value shouldBe "/shutter-events/list?environment=production"
    }

    "request shutter events from the shutter api for the specified environment" in new Fixture {
      val filter = ShutterEventsFilter(environment = Production, serviceName = None)
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName)(FakeRequest())

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "request shutter events from the shutter api for the specified service and environment" in new Fixture {
      val filter = ShutterEventsFilter(environment = Production, serviceName = Some("abc-frontend"))
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName)(FakeRequest())

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "ignore a blank service name" in new Fixture {
      val filter = ShutterEventsFilter(environment = Production, serviceName = None)
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = Some("    "))(FakeRequest())

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "recover from a failure to retrieve events from the shutter api" in new Fixture {
      val filter = ShutterEventsFilter(environment = Production, serviceName = None)
      stubConnectorFailure(filter)

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName)(FakeRequest())

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) shouldBe empty
    }
  }
}

private object ShutterEventsControllerSpec {
  val ShutterEventCssSelector = ".shutter-events > tbody > tr"

  def sampleEvent = ShutterStateChangeEvent(
    "username",
    timestamp = Instant.now(),
    "service name",
    environment = Development,
    shutterType = Frontend,
    status = Unshuttered,
    cause = UserCreated
  )
}