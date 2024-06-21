/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Helpers}
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterCause.UserCreated
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterStatus.Unshuttered
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType.Frontend

import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShutterEventsControllerSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with GuiceOneAppPerSuite
     with DefaultAwaitTimeout
     with OptionValues {

  import Helpers._
  import ShutterEventsControllerSpec._

  private trait Fixture {
    implicit val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val connector           = mock[ShutterConnector]
    val authStubBehaviour   = mock[StubBehaviour]
    val routeRulesConnector = mock[RouteRulesConnector]
    val authComponent       = FrontendAuthComponentsStub(authStubBehaviour)
    val underTest           = new ShutterEventsController(mcc, connector, routeRulesConnector, authComponent)

    when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
      .thenReturn(Future.unit)

    when(routeRulesConnector.frontendServices()(any[HeaderCarrier]))
      .thenReturn(Future.successful(Seq.empty[String]))

    def stubConnectorSuccess(forFilter: ShutterEventsFilter, returnEvents: Seq[ShutterStateChangeEvent] = Seq.empty): Unit =
      when(connector.shutterEventsByTimestampDesc(eqTo(forFilter.copy(serviceName = None)), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(returnEvents))

    def stubConnectorFailure(forFilter: ShutterEventsFilter): Unit =
      when(connector.shutterEventsByTimestampDesc(eqTo(forFilter.copy(serviceName = None)), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("connector failure")))

    implicit class ResultOps(eventualResult: Future[Result]) {
      lazy val toDocument: Document = Jsoup.parse(contentAsString(eventualResult))
    }
  }

  private val fakeRequest =
    FakeRequest().withSession(SessionKeys.authToken -> "Token token")

  "Shutter Events Controller" should {
    "redirect from the shutter events entry point to the production shutter events list" in new Fixture {
      val futResult = underTest.shutterEvents(FakeRequest())

      redirectLocation(futResult).value shouldBe "/shutter-events/list?environment=production"
    }

    "request shutter events from the shutter api for the specified environment" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.Production, serviceName = None)
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName, None, None)(fakeRequest)

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "request shutter events from the shutter api for the specified service and environment" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.Production, serviceName = Some(ServiceName("abc-frontend")))
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName, None, None)(fakeRequest)

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "ignore a blank service name" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.Production, serviceName = None)
      stubConnectorSuccess(forFilter = filter, returnEvents = Seq(sampleEvent))

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = Some(ServiceName("    ")), None, None)(fakeRequest)

      status(futResult) shouldBe OK
      futResult.toDocument.select(ShutterEventCssSelector) should have length 1
    }

    "recover from a failure to retrieve events from the shutter api" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.Production, serviceName = None)
      stubConnectorFailure(filter)

      val futResult = underTest.shutterEventsList(env = filter.environment, serviceName = filter.serviceName, None, None)(fakeRequest)

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
    ServiceName("service name"),
    environment = Environment.Development,
    shutterType = Frontend,
    status      = Unshuttered,
    cause       = UserCreated
  )
}
