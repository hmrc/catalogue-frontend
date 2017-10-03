/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.gov.hmrc.play.config.ServicesConfig

trait UserManagementPortalLink extends ServicesConfig {
  val serviceName = "user-management"

  def umpMyTeamsPageUrl(teamName: String): String = {

    val frontPageUrlConfigKey = s"$serviceName.myTeamsUrl"
    val myTeamsUrl = getConfString(frontPageUrlConfigKey, throw new RuntimeException(s"Could not find config $frontPageUrlConfigKey"))

    s"""${myTeamsUrl.appendSlash}$teamName?edit"""

  }

  def userManagementBaseUrl: String =
    getConfString(s"$serviceName.url", throw new RuntimeException(s"Could not find config $serviceName.url"))

}
