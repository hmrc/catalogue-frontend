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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}
import play.api.mvc.QueryStringBindable

case class Progress(
  current : Int,
  target  : Int,
) {
  def percent: Int = if (target == 0) 0 else (current.toFloat / target.toFloat * 100).toInt
}

object Progress {
  implicit val format: OFormat[Progress] = {
    ((__ \ "current").format[Int]
      ~ (__ \ "target").format[Int]
      )(Progress.apply,unlift(Progress.unapply))
  }
}

case class PlatformInitiative(
   initiativeName       : String  ,
   initiativeDescription: String  ,
   progress             : Progress,
   completedLegend      : String  ,
   inProgressLegend     : String
)

object PlatformInitiative {
  val format: OFormat[PlatformInitiative] = {
    ((__ \ "initiativeName").format[String]
      ~ (__ \ "initiativeDescription").format[String]
      ~ (__ \ "progress"    ).format[Progress]
      ~ (__ \ "completedLegend").format[String]
      ~ (__ \ "inProgressLegend").format[String]
      ) (apply, unlift(unapply))
  }
}

sealed trait DisplayType {
  def asString: String
}
object DisplayType {
  case object Progress  extends DisplayType { override val asString = "Progress" }
  case object Chart     extends DisplayType { override val asString = "Chart"    }

  val values: List[DisplayType] = List(Chart, Progress)

  def parse(s: String): Option[DisplayType] =
    values.find(_.asString == s)

  implicit def queryStringBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[DisplayType] =
    new QueryStringBindable[DisplayType] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, DisplayType]] =
        params.get(key).flatMap(_.headOption.map(DisplayType.parse)).map {
          case None               => Left(s"Unsupported Display Type")
          case Some(displayType)  => Right(displayType)
        }

      override def unbind(key: String, value: DisplayType): String =
        strBinder.unbind(key, value.asString)
    }
}
