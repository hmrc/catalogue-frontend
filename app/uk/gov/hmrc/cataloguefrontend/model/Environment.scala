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

import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}

enum Environment(val asString: String, val displayString: String) extends FromString:
  case Integration  extends Environment(asString = "integration" , displayString = "Integration"  )
  case Development  extends Environment(asString = "development" , displayString = "Development"  )
  case QA           extends Environment(asString = "qa"          , displayString = "QA"           )
  case Staging      extends Environment(asString = "staging"     , displayString = "Staging"      )
  case ExternalTest extends Environment(asString = "externaltest", displayString = "External Test")
  case Production   extends Environment(asString = "production"  , displayString = "Production"   )

object Environment extends FromStringEnum[Environment]

trait SlugInfoFlag { def asString: String; def displayString: String }
object SlugInfoFlag {
  case object Latest                          extends SlugInfoFlag { override def asString = "latest"    ; override def displayString = "Latest"          }
  case class ForEnvironment(env: Environment) extends SlugInfoFlag { override def asString = env.asString; override def displayString = env.displayString }

  val values: Seq[SlugInfoFlag] =
    Latest +: Environment.valuesAsSeq.map(ForEnvironment.apply)

  implicit val ordering: Ordering[SlugInfoFlag] =
    Ordering.by(values.indexOf(_))

  def parse(s: String): Option[SlugInfoFlag] =
    if (s == "external test")
      Some(SlugInfoFlag.ForEnvironment(Environment.ExternalTest)) // service-dependencies currently represents with a space
    else values.find(_.asString == s)
}
