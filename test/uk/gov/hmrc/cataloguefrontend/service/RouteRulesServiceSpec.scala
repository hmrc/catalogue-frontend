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
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector.{EnvironmentRoute, Route}
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.ServiceRoutes

class RouteRulesServiceSpec extends AnyWordSpec with Matchers {

  "Service" should {
    "return no result for inconsistency check when no environment routes" in {
      val inconsistentRoutes = ServiceRoutes(Nil).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe false
    }

    "determine if there is inconsistency in the public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa",
          Seq(Route("frontendPath", "ruleConfigurationUrlQa"),
          Route("inconsistent", "ruleConfigurationUrlQa")))
      )

      val inconsistentRoutes = ServiceRoutes(environmentRoutes).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe true
      inconsistentRoutes.head.environment shouldBe "qa"
      inconsistentRoutes.head.routes.length shouldBe 1
      inconsistentRoutes.head.routes.head.frontendPath shouldBe "inconsistent"
    }

    "determine if there is inconsistency with public URL rules when duplicates exist" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute("qa", Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      val inconsistentRoutes = ServiceRoutes(environmentRoutes).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe true
      inconsistentRoutes.head.environment shouldBe "qa"
    }

    "determine if there is consistency with public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute("qa", Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "be consistent when no routes" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Nil),
        EnvironmentRoute("qa", Nil)
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "return Production environment route as default reference route" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa", Seq(Route("inconsistent", "ruleConfigurationUrl")))
      )

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe true
    }

    "return next environment route as reference when no production" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("development", Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa", Seq(Route("inconsistent", "ruleConfigurationUrl")))
      )

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe true
    }

    "return no reference environment when no environment routes" in {
      val environmentRoutes: Seq[EnvironmentRoute] = Nil

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe false
    }

    "handle Admin and Frontend routes" in {
      val adminRoutes = Seq(
        EnvironmentRoute(
          environment = "qa",
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ),
        EnvironmentRoute(
          environment = "production",
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ),
        EnvironmentRoute(
          environment = "staging",
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ))

      val frontendRoutes = Seq(
        EnvironmentRoute(
          environment = "qa",
          routes      = Seq(Route(
                          frontendPath         = "/fhdds",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                       )),
          isAdmin     = false
        ),
        EnvironmentRoute(
          environment = "staging",
          routes      = Seq(Route(
                          frontendPath         = "/fhdds",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
        ),
        EnvironmentRoute(
        environment = "production",
          routes    = Seq(Route(
                        frontendPath         = "/fhdds",
                        ruleConfigurationUrl = "",
                        isRegex              = false
                      )),
        ),
        EnvironmentRoute(
        environment = "integration",
          routes    = Seq(Route(
                        frontendPath         = "/fhdds",
                        ruleConfigurationUrl = "",
                        isRegex              = false
                      )),
        ),
        EnvironmentRoute(
        environment = "development",
          routes    = Seq(Route(
                        frontendPath         = "/fhdds",
                        ruleConfigurationUrl = "",
                        isRegex              = false
                      ))
          )
      )

      val inconsistentRoutes = ServiceRoutes(adminRoutes ++ frontendRoutes).inconsistentRoutes
      // we only show additional routes in lower envs, not missing routes (currently..)
      inconsistentRoutes.nonEmpty shouldBe false
    }
  }
}
