/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.config

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class CatalogueConfig @Inject() (servicesConfig: ServicesConfig) {
  val shutterGroup         = servicesConfig.getString("perms.shutter.group")
  val shutterPlatformGroup = servicesConfig.getString("perms.shutter-platform.group")

  private def killswitchJenkinsUrl(env: String) =
    servicesConfig.getString(s"killswitch.jenkins-url.$env")

  private def killswitchJenkinsJob(shutterType: String) =
    servicesConfig.getString(s"killswitch.jenkins-job.$shutterType")

  def killSwitchLink(shutterType: String, env: String) =
    s"${killswitchJenkinsUrl(env)}/job/${killswitchJenkinsJob(shutterType)}/"
}
