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

package uk.gov.hmrc.cataloguefrontend

case class FeatureSwitch(name: String, isEnabled: Boolean)

object FeatureSwitch:
  def forName(name: String): FeatureSwitch =
    FeatureSwitch(name, java.lang.Boolean.getBoolean(systemPropertyName(name)))

  private def systemPropertyName(name: String) =
    s"feature.$name"


object CatalogueFrontendSwitches:
  def requiresLogin: FeatureSwitch =
    FeatureSwitch.forName("requires-login")

  def showHealthMetricsTimeline: FeatureSwitch =
    FeatureSwitch.forName("show-health-metrics-timeline")

  def disableDeployment: FeatureSwitch =
    FeatureSwitch.forName("disable-deployments")