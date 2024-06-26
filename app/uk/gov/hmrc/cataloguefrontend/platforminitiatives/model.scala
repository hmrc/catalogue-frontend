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
import play.api.libs.json.{Format, Reads, Writes, __}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser}

import FromStringEnum._

case class Progress(
  current : Int,
  target  : Int,
):
  def percent: Int =
    if target == 0
    then 0
    else (current.toFloat / target.toFloat * 100).toInt

object Progress {
  val format: Format[Progress] =
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

object PlatformInitiative:
  val format: Format[PlatformInitiative] =
    ( (__ \ "initiativeName"       ).format[String]
    ~ (__ \ "initiativeDescription").format[String]
    ~ (__ \ "progress"             ).format[Progress](Progress.format)
    ~ (__ \ "completedLegend"      ).format[String]
    ~ (__ \ "inProgressLegend"     ).format[String]
    )(apply, pi => Tuple.fromProductTyped(pi))

given Parser[DisplayType] = Parser.parser(DisplayType.values)

enum DisplayType(
  override val asString: String
) extends FromString
  derives Ordering, PathBindable, QueryStringBindable:
  case Progress  extends DisplayType("Progress")
  case Chart     extends DisplayType("Chart"   )
