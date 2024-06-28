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

package uk.gov.hmrc.cataloguefrontend.util

import play.api.data.format.Formatter
import play.api.data.FormError
import play.api.libs.json.{JsError, JsString, JsSuccess, Reads, Writes}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.cataloguefrontend.binders.Binders

trait Parser[T] { def parse(s: String): Either[String, T] }

object Parser:
  def parser[T <: FromString](values: Array[T]): Parser[T] =
    (s: String) =>
      values
        .find(_.asString.equalsIgnoreCase(s))
        .toRight(s"Invalid value: \"$s\" - should be one of: ${values.map(_.asString).mkString(", ")}")

  def parse[T](s: String)(using parser: Parser[T]): Either[String, T] =
    parser.parse(s)

trait FromString { def asString: String }

trait FromStringEnum[T <: scala.reflect.Enum with FromString]:
  def values: Array[T]

  lazy val valuesAsSeq: Seq[T] =
    scala.collection.immutable.ArraySeq.unsafeWrapArray(values)

end FromStringEnum

// Formatter does not have a companion object - so we can't extend it (like we have for other type)
// lets create an alias (name shows our usage intent better) with companion object
trait FormFormat[A] extends Formatter[A]

object FormFormat:
  def derived[A <: FromString : Parser]: FormFormat[A] =
    new FormFormat[A]:
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
        data
          .get(key)
          .flatMap(Parser.parse[A](_).toOption)
          .fold[Either[Seq[FormError], A]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

      override def unbind(key: String, value: A): Map[String, String] =
        Map(key -> value.asString)

object FromStringEnum:
  import scala.deriving.Mirror

  extension (obj: Ordering.type)
    def derived[A <: scala.reflect.Enum]: Ordering[A] =
      Ordering.by(_.ordinal)

  extension (obj: Writes.type)
    def derived[A <: FromString]: Writes[A] =
      a => JsString(a.asString)

  extension (obj: Reads.type)
    def derived[A : Parser]: Reads[A] =
      _.validate[String]
        .flatMap(Parser.parse[A](_).fold(JsError(_), JsSuccess(_)))

  extension (obj: PathBindable.type)
    def derived[A <: FromString : Parser]: PathBindable[A] =
      Binders.pathBindableFromString(Parser.parse, _.asString)

  extension (obj: QueryStringBindable.type)
    def derived[A <: FromString : Parser]: QueryStringBindable[A] =
      Binders.queryStringBindableFromString(
        s => Some(Parser.parse[A](s)),
        _.asString
      )
