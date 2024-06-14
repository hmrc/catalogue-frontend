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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{OFormat, __}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}

case class Progress(
  current : Int,
  target  : Int,
) {
  def percent: Int =
    if (target == 0) 0
    else (current.toFloat / target.toFloat * 100).toInt
}

object Progress {
  implicit val format: OFormat[Progress] =
    ( (__ \ "current").format[Int]
    ~ (__ \ "target" ).format[Int]
    )(Progress.apply, p => Tuple.fromProductTyped(p))
}

case class PlatformInitiative(
  initiativeName       : String  ,
  initiativeDescription: String  ,
  progress             : Progress,
  completedLegend      : String  ,
  inProgressLegend     : String
)

object PlatformInitiative {
  val format: OFormat[PlatformInitiative] =
    ( (__ \ "initiativeName"       ).format[String]
    ~ (__ \ "initiativeDescription").format[String]
    ~ (__ \ "progress"             ).format[Progress]
    ~ (__ \ "completedLegend"      ).format[String]
    ~ (__ \ "inProgressLegend"     ).format[String]
    )(apply, pi => Tuple.fromProductTyped(pi))
}

enum DisplayType(val asString: String) extends FromString:
  case Progress  extends DisplayType("Progress")
  case Chart     extends DisplayType("Chart"   )

object DisplayType extends FromStringEnum[DisplayType]
