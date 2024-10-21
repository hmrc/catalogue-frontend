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

import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector.{Route, RouteType}
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, RouteRulesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, UserName}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ShutterServiceSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience {

  val mockShutterStates = Seq(
      ShutterState(
        serviceName  = ServiceName("abc-frontend")
      , context      = None
      , shutterType  = ShutterType.Frontend
      , environment  = Environment.Production
      , status       = ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
      )
    , ShutterState(
        serviceName  = ServiceName("zxy-frontend")
      , context      = None
      , shutterType  = ShutterType.Frontend
      , environment  = Environment.Production
      , status       = ShutterStatus.Unshuttered
      )
    , ShutterState(
        serviceName = ServiceName("ijk-frontend")
      , context      = None
      , shutterType  = ShutterType.Frontend
      , environment  = Environment.Production
      , status       = ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
      )
    )

  val mockEvents = Seq(
      ShutterStateChangeEvent(
          username    = UserName("test.user")
        , timestamp   = Instant.now().minus(2, ChronoUnit.DAYS)
        , serviceName = ServiceName("abc-frontend")
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = UserName("fake.user")
        , timestamp   = Instant.now()
        , serviceName = ServiceName("zxy-frontend")
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Unshuttered
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = UserName("test.user")
        , timestamp   = Instant.now().minus(1, ChronoUnit.DAYS)
        , serviceName = ServiceName("ijk-frontend")
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
        , cause       = ShutterCause.UserCreated
        )
    )

  "findCurrentStates" should {
    "return a list of shutter events ordered by shutter status" in {
      val boot = Boot.init
      given HeaderCarrier = HeaderCarrier()

      when(boot.mockShutterConnector.shutterStates(ShutterType.Frontend, Environment.Production))
        .thenReturn(Future.successful(mockShutterStates))
      when(boot.mockShutterConnector.latestShutterEvents(ShutterType.Frontend, Environment.Production))
        .thenReturn(Future.successful(mockEvents))

      val states = boot.shutterService.findCurrentStates(ShutterType.Frontend, Environment.Production).futureValue
      states.map(_._1.status) shouldBe Seq(
        ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
      , ShutterStatus.Shuttered(reason = None, outageMessage = None, outageMessageWelsh = None, useDefaultOutagePage = false)
      , ShutterStatus.Unshuttered
      )
    }
  }

  "lookupShutterRoute" should {
    "return a frontend route that is not regex" in {
      val boot = Boot.init
      given HeaderCarrier = HeaderCarrier()

      val service = ServiceName("service1")
      val env     = Environment.Production

      when(boot.mockRouteRulesConnector.routes(Some(service), Some(RouteType.Frontend), Some(env)))
        .thenReturn(Future.successful(Seq(
          Route(service, "/path1", Some(""), isRegex = false, RouteType.Frontend, Environment.Production),
          Route(service, "/path2", Some(""), isRegex = true , RouteType.Frontend, Environment.Production)
        )))

      val shutterRoute = boot.shutterService.lookupShutterRoute(service, env)
      shutterRoute.futureValue shouldBe Some("https://www.tax.service.gov.uk/path1/platops-shutter-testing")
    }
  }

  def mkOutagePage(serviceName: ServiceName, warnings: List[OutagePageWarning]): OutagePage =
    OutagePage(
        serviceName        = serviceName
      , serviceDisplayName = None
      , outagePageURL      = ""
      , warnings           = warnings
      , mainContent        = ""
      , templatedElements  = List.empty
      )

  case class Boot(shutterService: ShutterService, mockShutterConnector: ShutterConnector, mockRouteRulesConnector: RouteRulesConnector)

  object Boot {
    def init: Boot =
      val mockShutterConnector       = mock[ShutterConnector]
      val mockShutterGroupsConnector = mock[ShutterGroupsConnector]
      val mockRouteRulesConnector    = mock[RouteRulesConnector]
      val mockGithubProxyConnector   = mock[GitHubProxyConnector]
      val shutterService             = ShutterService(mockShutterConnector, mockShutterGroupsConnector, mockRouteRulesConnector, mockGithubProxyConnector)
      Boot(shutterService, mockShutterConnector, mockRouteRulesConnector)
  }
}
