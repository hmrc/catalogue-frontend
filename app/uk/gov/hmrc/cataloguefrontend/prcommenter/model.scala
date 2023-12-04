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

package uk.gov.hmrc.cataloguefrontend.prcommenter

import uk.gov.hmrc.cataloguefrontend.connector.model.{TeamName, Version}
import java.time.Instant

case class PrCommenterReport(
  name     : String,
  teamNames: List[TeamName],
  version  : Version,
  comments : Seq[PrCommenterComment],
  created  : Instant
)

case class PrCommenterComment(
  message    : String,
  commentType: String
)

object PrCommenterReport {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  private implicit val commentReads: Reads[PrCommenterComment] =
    ( (__ \ "message"      ).read[String]
    ~ (__ \ "params" \ "id").read[String]
    )(PrCommenterComment.apply _)

  val reads: Reads[PrCommenterReport] =
    ( (__ \ "name"     ).read[String]
    ~ (__ \ "teamNames").read[List[String]].map(_.map(TeamName.apply))
    ~ (__ \ "version"  ).read[Version](Version.format)
    ~ (__ \ "comments" ).read[Seq[PrCommenterComment]]
    ~ (__ \ "created"  ).read[Instant]
    )(PrCommenterReport.apply _)

}
