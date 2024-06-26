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
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.ServiceRoutes

class RouteRulesServiceSpec extends AnyWordSpec with Matchers {

  "Service" should {
    "return no result for inconsistency check when no environment routes" in {
      val inconsistentRoutes = ServiceRoutes(Nil).inconsistentRoutes
      inconsistentRoutes.nonEmpty shouldBe false
    }

    "determine if there is inconsistency in the public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Production, Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute(Environment.QA,
          Seq(
            Route("frontendPath", "ruleConfigurationUrlQa"),
            Route("inconsistent", "ruleConfigurationUrlQa")
          )
        )
      )

      val inconsistentRoutes = ServiceRoutes(environmentRoutes).inconsistentRoutes
      inconsistentRoutes.nonEmpty                      shouldBe true
      inconsistentRoutes.head.environment              shouldBe Environment.QA
      inconsistentRoutes.head.routes.length            shouldBe 1
      inconsistentRoutes.head.routes.head.frontendPath shouldBe "inconsistent"
    }

    "determine if there is inconsistency with public URL rules when duplicates exist" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Production, Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute(Environment.QA, Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      val inconsistentRoutes = ServiceRoutes(environmentRoutes).inconsistentRoutes
      inconsistentRoutes.nonEmpty         shouldBe true
      inconsistentRoutes.head.environment shouldBe Environment.QA
    }

    "determine if there is consistency with public URL rules" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Production, Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        )),
        EnvironmentRoute(Environment.QA, Seq(
          Route("frontendPathOne", "ruleConfigurationUrlOne"),
          Route("frontendPathTwo", "ruleConfigurationUrlTwo")
        ))
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "be consistent when no routes" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Production, Seq.empty),
        EnvironmentRoute(Environment.QA        , Seq.empty)
      )

      ServiceRoutes(environmentRoutes).inconsistentRoutes.nonEmpty shouldBe false
    }

    "return Production environment route as default reference route" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Production, Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute(Environment.QA        , Seq(Route("inconsistent", "ruleConfigurationUrl")))
      )

      ServiceRoutes(environmentRoutes).referenceEnvironmentRoutes.isDefined shouldBe true
    }

    "return next environment route as reference when no production" in {
      val environmentRoutes = Seq(
        EnvironmentRoute(Environment.Development, Seq(Route("frontendPath", "ruleConfigurationUrl"))),
        EnvironmentRoute(Environment.QA         , Seq(Route("inconsistent", "ruleConfigurationUrl")))
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
          environment = Environment.QA,
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ),
        EnvironmentRoute(
          environment = Environment.Production,
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ),
        EnvironmentRoute(
          environment = Environment.Staging,
          routes      = Seq(Route(
                          frontendPath         = "/fh-admin-page",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
          isAdmin     = true
        ))

      val frontendRoutes = Seq(
        EnvironmentRoute(
          environment = Environment.QA,
          routes      = Seq(Route(
                          frontendPath         = "/fhdds",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                       )),
          isAdmin     = false
        ),
        EnvironmentRoute(
          environment = Environment.Staging,
          routes      = Seq(Route(
                          frontendPath         = "/fhdds",
                          ruleConfigurationUrl = "",
                          isRegex              = false
                        )),
        ),
        EnvironmentRoute(
        environment = Environment.Production,
          routes    = Seq(Route(
                        frontendPath         = "/fhdds",
                        ruleConfigurationUrl = "",
                        isRegex              = false
                      )),
        ),
        EnvironmentRoute(
        environment = Environment.Integration,
          routes    = Seq(Route(
                        frontendPath         = "/fhdds",
                        ruleConfigurationUrl = "",
                        isRegex              = false
                      )),
        ),
        EnvironmentRoute(
        environment = Environment.Development,
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
