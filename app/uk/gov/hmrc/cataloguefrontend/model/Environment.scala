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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}
import play.api.mvc.{PathBindable, QueryStringBindable}
import play.api.data.format.Formatter
import play.api.data.FormError

enum Environment(val asString: String, val displayString: String):
  case Integration  extends Environment(asString = "integration" , displayString = "Integration"  )
  case Development  extends Environment(asString = "development" , displayString = "Development"  )
  case QA           extends Environment(asString = "qa"          , displayString = "QA"           )
  case Staging      extends Environment(asString = "staging"     , displayString = "Staging"      )
  case ExternalTest extends Environment(asString = "externaltest", displayString = "External Test")
  case Production   extends Environment(asString = "production"  , displayString = "Production"   )

object Environment {
  val valuesAsSeq: Seq[Environment] =
    scala.collection.immutable.ArraySeq.unsafeWrapArray(values)

  implicit val ordering: Ordering[Environment] =
    Ordering.by(_.ordinal)

  def parse(s: String): Option[Environment] =
    values.find(_.asString == s)

  val format: Format[Environment] = new Format[Environment] {
    override def writes(o: Environment): JsValue = JsString(o.asString)
    override def reads(json: JsValue): JsResult[Environment] =
      json.validate[String].flatMap(s => Environment.parse(s).map(e => JsSuccess(e)).getOrElse(JsError("invalid environment")))
  }

  implicit val pathBindable: PathBindable[Environment] =
    new PathBindable[Environment] {
      override def bind(key: String, value: String): Either[String, Environment] =
        parse(value).toRight(s"Invalid Environment '$value'")

      override def unbind(key: String, value: Environment): String =
        value.asString
    }

  implicit def queryStringBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[Environment] =
    new QueryStringBindable[Environment] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Environment]] =
        strBinder.bind(key, params)
          .map(_.flatMap { value => parse(value).toRight(s"Invalid Environment '$value'") })

      override def unbind(key: String, value: Environment): String =
        strBinder.unbind(key, value.asString)
    }

  val formFormat: Formatter[Environment] = new Formatter[Environment] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Environment] =
      data
        .get(key)
        .flatMap(parse(_))
        .fold[Either[Seq[FormError], Environment]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

    override def unbind(key: String, value: Environment): Map[String, String] =
      Map(key -> value.asString)
  }
}

trait SlugInfoFlag { def asString: String; def displayString: String }
object SlugInfoFlag {
  case object Latest                          extends SlugInfoFlag { override def asString = "latest"    ; override def displayString = "Latest"          }
  case class ForEnvironment(env: Environment) extends SlugInfoFlag { override def asString = env.asString; override def displayString = env.displayString }

  val values: Seq[SlugInfoFlag] =
    Latest +: Environment.valuesAsSeq.map(ForEnvironment.apply)

  implicit val ordering: Ordering[SlugInfoFlag] =
    new Ordering[SlugInfoFlag] {
      def compare(x: SlugInfoFlag, y: SlugInfoFlag): Int =
        values.indexOf(x).compare(values.indexOf(y))
    }

  def parse(s: String): Option[SlugInfoFlag] =
    if (s == "external test")
      Some(SlugInfoFlag.ForEnvironment(Environment.ExternalTest)) // service-dependencies currently represents with a space
    else values.find(_.asString == s)
}
