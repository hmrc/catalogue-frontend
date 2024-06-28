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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser, FormFormat}

import FromStringEnum._

given Parser[CurationStatus] = Parser.parser(CurationStatus.values)

enum CurationStatus(val asString: String, val display: String) extends FromString derives Ordering, Reads, FormFormat:
  case InvestigationOngoing extends CurationStatus(asString = "INVESTIGATION_ONGOING", display = "Investigation ongoing")
  case NoActionRequired     extends CurationStatus(asString = "NO_ACTION_REQUIRED"   , display = "No action required"   )
  case ActionRequired       extends CurationStatus(asString = "ACTION_REQUIRED"      , display = "Action required"      )
  case Uncurated            extends CurationStatus(asString = "UNCURATED"            , display = "Uncurated"            )

object CurationStatus extends FromStringEnum[CurationStatus]
