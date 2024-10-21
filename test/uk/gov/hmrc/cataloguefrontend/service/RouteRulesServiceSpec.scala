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

package uk.gov.hmrc.cataloguefrontend.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector.{Route, RouteType}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}

class RouteRulesServiceSpec extends AnyWordSpec with Matchers:

  private val routeRulesService = RouteRulesService()
  private val service           = ServiceName("service-1")

  "Service" should {
    "return no result for inconsistency check when no environment routes" in {
      val inconsistentRoutes = Seq.empty[Route]
      inconsistentRoutes.nonEmpty shouldBe false
    }

    "determine if there is inconsistency in the public URL rules" in {
      val routes = Seq(
        Route(service, "frontendPath", Some("ruleConfigurationUrl"),   false, RouteType.Frontend, Environment.Production),
        Route(service, "frontendPath", Some("ruleConfigurationUrlQa"), false, RouteType.Frontend, Environment.QA        ),
        Route(service, "inconsistent", Some("ruleConfigurationUrlQa"), false, RouteType.Frontend, Environment.QA        )
      )

      val inconsistentRoutes = routeRulesService.inconsistentRoutes(routes)
      inconsistentRoutes.nonEmpty         shouldBe true
      inconsistentRoutes.head.environment shouldBe Environment.QA
      inconsistentRoutes.length           shouldBe 1
      inconsistentRoutes.head.path        shouldBe "inconsistent"
    }

    "determine if there is inconsistency with public URL rules when duplicates exist" in {
      val routes = Seq(
        Route(service, "frontendPathOne", Some("ruleConfigurationUrlOne"), false, RouteType.Frontend, Environment.Production),
        Route(service, "frontendPathTwo", Some("ruleConfigurationUrlTwo"), false, RouteType.Frontend, Environment.Production),
        Route(service, "frontendPathOne", Some("ruleConfigurationUrlOne"), false, RouteType.Frontend, Environment.QA        ),
        Route(service, "frontendPathTwo", Some("ruleConfigurationUrlTwo"), false, RouteType.Frontend, Environment.QA        ),
        Route(service, "frontendPathTwo", Some("ruleConfigurationUrlTwo"), false, RouteType.Frontend, Environment.QA        )
      )

      val inconsistentRoutes = routeRulesService.inconsistentRoutes(routes)
      inconsistentRoutes.nonEmpty         shouldBe true
      inconsistentRoutes.head.environment shouldBe Environment.QA
    }

    "determine if there is consistency with public URL rules" in {
      val routes = Seq(
          Route(service, "frontendPathOne", Some("ruleConfigurationUrlOne"), false, RouteType.Frontend, Environment.Production),
          Route(service, "frontendPathTwo", Some("ruleConfigurationUrlTwo"), false, RouteType.Frontend, Environment.Production),
          Route(service, "frontendPathOne", Some("ruleConfigurationUrlOne"), false, RouteType.Frontend, Environment.QA        ),
          Route(service, "frontendPathTwo", Some("ruleConfigurationUrlTwo"), false, RouteType.Frontend, Environment.QA        )
      )

      val inconsistentRoutes = routeRulesService.inconsistentRoutes(routes)

      inconsistentRoutes.nonEmpty shouldBe false
    }

    "determine if there is inconsistency in the URL paths" in {
      val routes = Seq(
        Route(service, "frontendPath", Some("ruleConfigurationUrl"), false, RouteType.Frontend, Environment.Production),
        Route(service, "frontendPath", Some("ruleConfigurationUrl"), false, RouteType.Frontend, Environment.QA        ),
        Route(service, "inconsistent", Some("ruleConfigurationUrl"), false, RouteType.Frontend, Environment.QA        )
      )

      val inconsistentRoutes = routeRulesService.inconsistentRoutes(routes)
      inconsistentRoutes shouldBe Seq(
        Route(service, "inconsistent", Some("ruleConfigurationUrl"), false, RouteType.Frontend, Environment.QA)
      )
    }

    "be consistent when no routes" in {
      val routes = Seq.empty[Route]
      
      val inconsistentRoutes = routeRulesService.inconsistentRoutes(routes)

      inconsistentRoutes.nonEmpty shouldBe false
    }

    "handle Admin and Frontend routes" in {
      val adminRoutes = Seq(
        Route(service, "/fh/admin-page", Some(""), false, RouteType.AdminFrontend, Environment.QA        ),
        Route(service, "/fh/admin-page", Some(""), false, RouteType.AdminFrontend, Environment.Production),
        Route(service, "/fh/admin-page", Some(""), false, RouteType.AdminFrontend, Environment.Staging   )
      )

      val frontendRoutes = Seq(
        Route(service, "/fhdds", Some(""), false, RouteType.Frontend, Environment.QA         ),
        Route(service, "/fhdds", Some(""), false, RouteType.Frontend, Environment.Staging    ),
        Route(service, "/fhdds", Some(""), false, RouteType.Frontend, Environment.Production ),
        Route(service, "/fhdds", Some(""), false, RouteType.Frontend, Environment.Integration),
        Route(service, "/fhdds", Some(""), false, RouteType.Frontend, Environment.Development)
      )
      
      val inconsistentRoutes = routeRulesService.inconsistentRoutes(adminRoutes ++ frontendRoutes)
      // we only show additional routes in lower envs, not missing routes (currently..)
      inconsistentRoutes.nonEmpty shouldBe false
    }
  }
