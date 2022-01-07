/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.{EnvironmentRoute, ServiceRoutes}

class RouteRulesServiceSpec extends AnyWordSpec with Matchers {

  "Service" should {
    "No result for inconsistency check when no environment routes" in {
      val inconsistentRoutes = ServiceRoutes(Nil).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe false
    }

    "determine if there is inconsistency in the public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(RouteRulesService.Route("frontendPath", "backendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa",
          Seq(RouteRulesService.Route("frontendPath", "backendPathQa", "ruleConfigurationUrlQa"),
          RouteRulesService.Route("inconsistent", "backendPathQa", "ruleConfigurationUrlQa")))
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
          RouteRulesService.Route("frontendPathOne", "backendPathOne", "ruleConfigurationUrlOne"),
          RouteRulesService.Route("frontendPathTwo", "backendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute("qa", Seq(
          RouteRulesService.Route("frontendPathOne", "backendPathOne", "ruleConfigurationUrlOne"),
          RouteRulesService.Route("frontendPathTwo", "backendPathTwo", "ruleConfigurationUrlTwo"),
          RouteRulesService.Route("frontendPathTwo", "backendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      val inconsistentRoutes = ServiceRoutes(environmentRoutes).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe true
      inconsistentRoutes.head.environment shouldBe "qa"
    }

    "determine if there is consistency with public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(
          RouteRulesService.Route("frontendPathOne", "backendPathOne", "ruleConfigurationUrlOne"),
          RouteRulesService.Route("frontendPathTwo", "backendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute("qa", Seq(
          RouteRulesService.Route("frontendPathOne", "backendPathOne", "ruleConfigurationUrlOne"),
          RouteRulesService.Route("frontendPathTwo", "backendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "Is consistent when no routes" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Nil),
        EnvironmentRoute("qa", Nil)
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "Production environment route is default reference route" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("production", Seq(RouteRulesService.Route("frontendPath", "backendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa", Seq(RouteRulesService.Route("inconsistent", "backendPath", "ruleConfigurationUrl")))
      )

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe true
    }

    "Next environment route is reference when no production" in {
      val environmentRoutes = Seq(
        EnvironmentRoute("development", Seq(RouteRulesService.Route("frontendPath", "backendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute("qa", Seq(RouteRulesService.Route("inconsistent", "backendPath", "ruleConfigurationUrl")))
      )

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe true
    }

    "No reference environment when no environment routes" in {
      val environmentRoutes: Seq[EnvironmentRoute] = Nil

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe false
    }
  }
}
