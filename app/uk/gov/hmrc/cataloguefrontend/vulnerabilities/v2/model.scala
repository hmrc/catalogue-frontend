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

package models

import play.api.libs.json.{Format, Json}

case class TeamVulnerabilitySummary(
  shortestRemainingSlaDays: Option[Int],
  hasBreachedSla: Boolean,
  byPriority: OccurrencesByPriority,
  byState: OccurrencesByState,
  affectedServices: Int
)

object TeamVulnerabilitySummary:
  implicit val format: Format[TeamVulnerabilitySummary] = Json.format[TeamVulnerabilitySummary]

case class OccurrencesByPriority(immediate: Int, expedite: Int, schedule: Int, monitor: Int)

object OccurrencesByPriority:
  implicit val format: Format[OccurrencesByPriority] = Json.format[OccurrencesByPriority]

case class OccurrencesByState(
  actionable: Int,
  notApplicable: Int,
  noFixAvailable: Int,
  riskAccepted: Int,
  resolved: Int
)

object OccurrencesByState:
  implicit val format: Format[OccurrencesByState] = Json.format[OccurrencesByState]