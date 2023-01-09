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

package uk.gov.hmrc.cataloguefrontend.util

import uk.gov.hmrc.cataloguefrontend.connector.Link
import uk.gov.hmrc.cataloguefrontend.model.Environment

object TelemetryLinks {

  def create(name: String, template: String, env: Environment, serviceName: String): Link = {
    val url = template
      .replace(s"$${env}", UrlUtils.encodePathParam(env.asString))
      .replace(s"$${service}", UrlUtils.encodePathParam(serviceName))
    Link(name, name, url)
  }
}
