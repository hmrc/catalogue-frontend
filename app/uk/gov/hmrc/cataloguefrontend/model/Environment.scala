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

package uk.gov.hmrc.cataloguefrontend.model

import play.api.libs.json.{Reads, Writes}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}

import FromStringEnum._

given Parser[Environment] = Parser.parser(Environment.values)

enum Environment(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Ordering, Reads, Writes, FormFormat, PathBindable, QueryStringBindable:
  case Integration  extends Environment(asString = "integration" , displayString = "Integration"  )
  case Development  extends Environment(asString = "development" , displayString = "Development"  )
  case QA           extends Environment(asString = "qa"          , displayString = "QA"           )
  case Staging      extends Environment(asString = "staging"     , displayString = "Staging"      )
  case ExternalTest extends Environment(asString = "externaltest", displayString = "External Test")
  case Production   extends Environment(asString = "production"  , displayString = "Production"   )


given Parser[SlugInfoFlag] =
  // TODO service-dependencies currently represents `external test` with a space
  //Parser.parser(SlugInfoFlag.values)
  (s: String) =>
    if s == "external test"
    then Right(SlugInfoFlag.ForEnvironment(Environment.ExternalTest))
    else SlugInfoFlag.values
           .find(_.asString == s)
           .toRight(s"Invalid value: \"$s\" - should be one of: ${SlugInfoFlag.values.map(_.asString).mkString(", ")}")

trait SlugInfoFlag
  extends FromString
  derives FormFormat, Reads {
  def displayString: String
}

object SlugInfoFlag:
  case object Latest                          extends SlugInfoFlag { override def asString = "latest"    ; override def displayString = "Latest"          }
  case class ForEnvironment(env: Environment) extends SlugInfoFlag { override def asString = env.asString; override def displayString = env.displayString }

  val values: Array[SlugInfoFlag] =
    Latest +: Environment.values.map(ForEnvironment.apply)

  given Ordering[SlugInfoFlag] =
    Ordering.by(values.indexOf(_))
