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
import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Reads, Writes}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.cataloguefrontend.binders.Binders


trait FromString { def asString: String }

trait FromStringEnum[T <: scala.reflect.Enum with FromString]:
  def values: Array[T]

  lazy val valuesAsSeq: Seq[T] =
    scala.collection.immutable.ArraySeq.unsafeWrapArray(values)

  // given Ordering[T] =
  //   Ordering.by(_.ordinal)

  def parse(s: String): Either[String, T] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid value: \"$s\" - should be one of: ${values.map(_.asString).mkString(", ")}")

  // TODO can we replace this with `deriving Format, Formater, PathBindable, QueryStringBindable` as required?

  val reads: Reads[T] =
    _.validate[String]
     .flatMap(parse(_).fold(JsError(_), JsSuccess(_)))

  val writes: Writes[T] =
    t => JsString(t.asString)

  val format: Format[T] =
    Format[T](reads, writes)

  val formFormat: Formatter[T] =
    new Formatter[T]:
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], T] =
        data
          .get(key)
          .flatMap(parse(_).toOption)
          .fold[Either[Seq[FormError], T]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

      override def unbind(key: String, value: T): Map[String, String] =
        Map(key -> value.asString)

  given pathBindable: PathBindable[T] =
    Binders.pathBindableFromString(parse, _.asString)

  implicit val queryStringBindable: QueryStringBindable[T] =
    Binders.queryStringBindableFromString(
      s => Some(parse(s)),
      _.asString
    )

end FromStringEnum


object FromStringEnum:

  extension (obj: Ordering.type)
    def derived[A <: scala.reflect.Enum]: Ordering[A] =
      Ordering.by(_.ordinal)
