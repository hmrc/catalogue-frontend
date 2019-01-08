/*
 * Copyright 2018 HM Revenue & Customs
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

package view.partials

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.{EnvironmentRoute, ServiceRoutes}
import uk.gov.hmrc.cataloguefrontend.{CatalogueFrontendSwitches, FeatureSwitch}

class ServiceRouteRuleViolationsSpec extends WordSpec with Matchers {

  val misMatchedServiceRoutes = ServiceRoutes(Seq(
    EnvironmentRoute(
      environment = "EnvName0",
      routes      = Seq(RouteRulesService.Route("TestUrl0", "TestUrl0", "ruleConfigurationUrl0"))
    ),
    EnvironmentRoute(
      environment = "EnvName1",
      routes      = Seq(RouteRulesService.Route("TestUrl1", "TestUrl1", "ruleConfigurationUrl1"))
    )
  ))

  val matchingServiceRoutes = ServiceRoutes(Seq(
    EnvironmentRoute(
      environment = "EnvName0",
      routes      = Seq(RouteRulesService.Route("TestUrl0", "TestUrl0", "ruleConfigurationUrl0"))
    ),
    EnvironmentRoute(
      environment = "EnvName1",
      routes      = Seq(RouteRulesService.Route("TestUrl0", "TestUrl0", "ruleConfigurationUrl1"))
    )
  ))

  "ServiceRouteRuleViolations" should {
    "display when feature flag is enabled" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.serviceRouteRuleViolations(misMatchedServiceRoutes).body
      result should include ("id=\"routing-rule-violations\"")
    }

    "do not display when feature flag is disabled" in {
      FeatureSwitch.disable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.serviceRouteRuleViolations(misMatchedServiceRoutes).body
      result should not include ("id=\"routing-rule-violations\"")
    }

    "display when there are URLs not matching" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.serviceRouteRuleViolations(misMatchedServiceRoutes).body
      result should include ("id=\"routing-rule-violations\"")
    }

    "do not display when there are URLs are matching" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.serviceRouteRuleViolations(matchingServiceRoutes).body
      result should not include ("id=\"routing-rule-violations\"")
    }
  }
}
