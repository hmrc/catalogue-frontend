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
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
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
      , status       = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
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
      , status       = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      )
    )

  val mockEvents = Seq(
      ShutterStateChangeEvent(
          username    = UserName("test.user")
        , timestamp   = Instant.now().minus(2, ChronoUnit.DAYS)
        , serviceName = ServiceName("abc-frontend")
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
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
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
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
        ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      , ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      , ShutterStatus.Unshuttered
      )
    }
  }

  "toOutagePageStatus" should {
    val boot = Boot.init

    "handle missing OutagePage" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq(ServiceName("service1"))
        , outagePages  = List.empty
        ) shouldBe List(OutagePageStatus(
            serviceName = ServiceName("service1")
          , warning     = Some(( "No templatedMessage Element no outage-page"
                               , "Default outage page will be displayed."
                              ))
          ))
    }

    "handle multiple missing OutagePages" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq(ServiceName("service1"), ServiceName("service2"))
        , outagePages  = List.empty
        ) shouldBe List(
            OutagePageStatus(
                serviceName = ServiceName("service1")
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = ServiceName("service2")
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          )
    }

    "handle warnings" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq(ServiceName("service1"), ServiceName("service2"), ServiceName("service3"))
        , outagePages  = List(
                             mkOutagePage(
                                 serviceName = ServiceName("service1")
                               , warnings    = List(OutagePageWarning(
                                                   name    = "UnableToRetrievePage"
                                                 , message = "Unable to retrieve outage-page from Github"
                                                 ))

                               )
                           , mkOutagePage(
                                 serviceName = ServiceName("service2")
                               , warnings    = List(OutagePageWarning(
                                                   name    = "MalformedHTML"
                                                 , message = "The outage page was found to have some malformed html content"
                                                 ))
                               )
                           , mkOutagePage(
                                 serviceName = ServiceName("service3")
                               , warnings    = List(OutagePageWarning(
                                                   name    = "DuplicateTemplateElementIDs"
                                                 , message = "More than one template ID was found in the content for: templatedMessage"
                                                 ))
                               )
                           )
        ) shouldBe List(
            OutagePageStatus(
                serviceName = ServiceName("service1")
              , warning     = Some(( "Unable to retrieve outage-page from Github"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = ServiceName("service2")
              , warning     = Some(( "The outage page was found to have some malformed html content"
                                   , "Outage page will be sent as is, without updating templates."
                                  ))
              )
          , OutagePageStatus(
                serviceName = ServiceName("service3")
              , warning     = Some(( "More than one template ID was found in the content for: templatedMessage"
                                   , "All matching elements will be updated"
                                  ))
              )
          )
    }
  }

  def mkOutagePage(serviceName: ServiceName, warnings: List[OutagePageWarning]): OutagePage =
    OutagePage(
        serviceName      = serviceName
      , environment      = Environment.Production
      , outagePageURL    = ""
      , warnings         = warnings
      , templatedElements = List.empty
      )

  case class Boot(shutterService: ShutterService, mockShutterConnector: ShutterConnector)

  object Boot {
    def init: Boot =
      val mockShutterConnector       = mock[ShutterConnector]
      val mockShutterGroupsConnector = mock[ShutterGroupsConnector]
      val routeRulesConnector        = mock[RouteRulesConnector]
      val shutterService             = ShutterService(mockShutterConnector, mockShutterGroupsConnector, routeRulesConnector)
      Boot(shutterService, mockShutterConnector)
  }
}
