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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}

import java.time.Instant


case class VulnerabilitiesTimelineCount(
  weekBeginning: Instant,
  actionRequired: Int,
  investigationOngoing: Int,
  noActionRequired: Int,
  uncurated: Int,
  total: Int
)

object VulnerabilitiesTimelineCount {
  val reads: Reads[VulnerabilitiesTimelineCount] = {
    ( (__ \ "weekBeginning"       ).read[Instant]
      ~ (__ \ "actionNeeded"        ).read[Int]
      ~ (__ \ "investigationOngoing").read[Int]
      ~ (__ \ "noActionRequired"    ).read[Int]
      ~ (__ \ "uncurated"           ).read[Int]
      ~ (__ \ "total"               ).read[Int]
      )(VulnerabilitiesTimelineCount.apply _)
  }
}
