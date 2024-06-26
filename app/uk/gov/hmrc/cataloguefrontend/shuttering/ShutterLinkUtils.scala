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

package uk.gov.hmrc.cataloguefrontend.shuttering

import uk.gov.hmrc.cataloguefrontend.model.Environment

object ShutterLinkUtils:
  def hostName(env: Environment): String =
    env match
      case Environment.Production => "www.tax.service.gov.uk"
      case _                      => s"www.${env.asString}.tax.service.gov.uk"

  def mkLink(env: Environment, serviceLink: String): String =
    s"https://${hostName(env)}$serviceLink/platops-shutter-testing"
