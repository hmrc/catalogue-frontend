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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}

case class VulnerabilitiesCount(
  actionRequired   : Int
, investigation    : Int
, noActionRequired : Int
)

object VulnerabilitiesCount {
  val reads: Reads[VulnerabilitiesCount] =
    ((__ \ "actionRequired").read[Int]
      ~ (__ \ "investigation").read[Int]
      ~ (__ \ "noActionRequired").read[Int]
      )(VulnerabilitiesCount.apply _)
}

case class VulnerabilitiesPerEnvironment(
  service     : String
, qa          : VulnerabilitiesCount
, staging     : VulnerabilitiesCount
, externalTest: VulnerabilitiesCount
, production  : VulnerabilitiesCount
)

object VulnerabilitiesPerEnvironment {

  private implicit val vcReads: Reads[VulnerabilitiesCount] = VulnerabilitiesCount.reads

  val reads: Reads[VulnerabilitiesPerEnvironment] =
    ((__ \ "service").read[String]
      ~ (__ \ "qa").read[VulnerabilitiesCount]
      ~ (__ \ "staging").read[VulnerabilitiesCount]
      ~ (__ \ "externalTest").read[VulnerabilitiesCount]
      ~ (__ \ "production").read[VulnerabilitiesCount]
      )(VulnerabilitiesPerEnvironment.apply _)
}