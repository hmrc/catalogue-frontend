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

package uk.gov.hmrc.cataloguefrontend.view.partials.html

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.RouteConfigurationConnector.{Route, RouteType}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService

class ServiceRouteRuleViolationsSpec extends AnyWordSpec with Matchers {
  
  private val routeRuleService = RouteRulesService()
  private val service          = ServiceName("service-1")

  val misMatchedRoutes = Seq(
    Route(service, "TestUrl0", Some("ruleConfigurationUrl0"), false, RouteType.Frontend, Environment.Development),
    Route(service, "TestUrl1", Some("ruleConfigurationUrl1"), false, RouteType.Frontend, Environment.Production )
  )
    

  val matchingRoutes = Seq(
    Route(service, "TestUrl0", Some("ruleConfigurationUrl0"), false, RouteType.Frontend, Environment.Development),
    Route(service, "TestUrl0", Some("ruleConfigurationUrl1"), false, RouteType.Frontend, Environment.Production )
  )

  "ServiceRouteRuleViolations" should {
    "display when there are URLs not matching" in {
      val inconsistentRoutes = routeRuleService.inconsistentRoutes(misMatchedRoutes)
      val result             = serviceRouteRuleViolations(inconsistentRoutes).body
      result should include ("id=\"routing-rule-violations\"")
    }

    "do not display when there are URLs matching" in {
      val inconsistentRoutes = routeRuleService.inconsistentRoutes(matchingRoutes)
      val result             = serviceRouteRuleViolations(inconsistentRoutes).body
      result should not include ("id=\"routing-rule-violations\"")
    }
  }
}
