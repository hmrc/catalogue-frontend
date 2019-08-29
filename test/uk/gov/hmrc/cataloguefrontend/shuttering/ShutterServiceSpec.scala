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
import java.time.temporal.ChronoUnit

import org.scalatest.{Matchers, WordSpec}
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ShutterServiceSpec extends WordSpec with MockitoSugar with Matchers {

  val mockShutterStates = Seq(
      ShutterState(
        name        = "abc-frontend"
      , environment = Environment.Production
      , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None)
      )
    , ShutterState(
        name        = "zxy-frontend"
      , environment = Environment.Production
      , status      = ShutterStatus.Unshuttered
      )
    , ShutterState(
        name        = "ijk-frontend"
      , environment = Environment.Production
      , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None)
      )
    )

  val mockEvents = Seq(
      ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(2, ChronoUnit.DAYS)
        , serviceName = "abc-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None)
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "fake.user"
        , timestamp   = Instant.now()
        , serviceName = "zxy-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Unshuttered
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(1, ChronoUnit.DAYS)
        , serviceName = "ijk-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None)
        , cause       = ShutterCause.UserCreated
        )
    )

  "findCurrentState" should {
    "return a list of shutter events ordered by shutter status" in {
      val boot = Boot.init
      implicit val hc = new HeaderCarrier()

      when(boot.mockShutterConnector.shutterStates(Environment.Production)).thenReturn(Future(mockShutterStates))
      when(boot.mockShutterConnector.latestShutterEvents(Environment.Production)).thenReturn(Future(mockEvents))

      val Seq(a,b,c) = Await.result(boot.shutterService.findCurrentState(Environment.Production), Duration(10, "seconds"))

      a.status shouldBe ShutterStatus.Shuttered(reason = None, outageMessage = None)
      b.status shouldBe ShutterStatus.Shuttered(reason = None, outageMessage = None)
      c.status shouldBe ShutterStatus.Unshuttered
    }
  }

  "toOutagePageStatus" should {
    val boot = Boot.init

    "handle missing OutagePage" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1")
        , outagePages  = List.empty
        ) shouldBe List(OutagePageStatus(
            serviceName = "service1"
          , warning     = Some(( "No templatedMessage Element no outage-page"
                               , "Default outage page will be displayed."
                              ))
          ))
    }

    "handle multiple missing OutagePages" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1", "service2")
        , outagePages  = List.empty
        ) shouldBe List(
            OutagePageStatus(
                serviceName = "service1"
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service2"
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          )
    }

    "handle warnings" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1", "service2", "service3")
        , outagePages  = List(
                             mkOutagePage(
                                 serviceName = "service1"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "UnableToRetrievePage"
                                                 , message = "Unable to retrieve outage-page from Github"
                                                 ))

                               )
                           , mkOutagePage(
                                 serviceName = "service2"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "MalformedHTML"
                                                 , message = "The outage page was found to have some malformed html content"
                                                 ))
                               )
                           , mkOutagePage(
                                 serviceName = "service3"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "DuplicateTemplateElementIDs"
                                                 , message = "More than one template ID was found in the content for: templatedMessage"
                                                 ))
                               )
                           )
        ) shouldBe List(
            OutagePageStatus(
                serviceName = "service1"
              , warning     = Some(( "Unable to retrieve outage-page from Github"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service2"
              , warning     = Some(( "The outage page was found to have some malformed html content"
                                   , "Outage page will be sent as is, without updating templates."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service3"
              , warning     = Some(( "More than one template ID was found in the content for: templatedMessage"
                                   , "All matching elements will be updated"
                                  ))
              )
          )
    }
  }

  def mkOutagePage(serviceName: String, warnings: List[OutagePageWarning]): OutagePage =
    OutagePage(
        serviceName      = serviceName
      , environment      = Environment.Production
      , outagePageURL    = ""
      , warnings         = warnings
      , templatedElements = List.empty
      )

  case class Boot(shutterService: ShutterService, mockShutterConnector: ShutterConnector)

  object Boot {
    def init: Boot = {
      val mockShutterConnector       = mock[ShutterConnector]
      val mockShutterGroupsConnector = mock[ShutterGroupsConnector]
      val shutterService             = new ShutterService(mockShutterConnector, mockShutterGroupsConnector)
      Boot(shutterService, mockShutterConnector)
    }
  }
}
