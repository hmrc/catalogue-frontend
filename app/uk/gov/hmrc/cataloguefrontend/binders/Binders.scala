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

package uk.gov.hmrc.cataloguefrontend.binders

import cats.implicits._
import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.mvc.{PathBindable, QueryStringBindable}

import java.time.{Instant, LocalDate}
import scala.util.Try

object Binders:

  implicit val instantQueryStringBindable: QueryStringBindable[Instant] =
    queryStringBindableFromString[Instant](
      s => Some(Try(Instant.parse(s)).toEither.left.map(_.getMessage)),
      _.toString
    )

  implicit val localDateQueryStringBindable: QueryStringBindable[LocalDate] =
    queryStringBindableFromString[LocalDate](
      s => Some(Try(LocalDate.parse(s)).toEither.left.map(_.getMessage)),
      _.toString
    )

  /** `summon[QueryStringBindable[String]].transform` doesn't allow us to provide failures.
    * This function provides `andThen` semantics
    */
  def queryStringBindableFromString[T](parse: String => Option[Either[String, T]], asString: T => String)(using strBinder: QueryStringBindable[String]): QueryStringBindable[T] =
    new QueryStringBindable[T]:
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, T]] =
        strBinder.bind(key, params) match
          case Some(Right(s)) => parse(s)
          case _              => None

      override def unbind(key: String, value: T): String =
        strBinder.unbind(key, asString(value))

  /** `summon[PathBindable[String]].transform` doesn't allow us to provide failures.
    * This function provides `andThen` semantics
    */
  def pathBindableFromString[T](parse: String => Either[String, T], asString: T => String)(using strBinder: PathBindable[String]): PathBindable[T] =
    new PathBindable[T]:
      override def bind(key: String, value: String): Either[String, T] =
        parse(value)

      override def unbind(key: String, value: T): String =
        asString(value)

  def formFormatFromString[T](parse: String => Either[String, T], asString: T => String): Formatter[T] =
    new Formatter[T]:
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], T] =
        data
          .get(key)
          .map(_.trim) match
            case Some(s) if s.nonEmpty => parse(s).leftMap(err => Seq(FormError(key, err)))
            case _                     => Left(Seq(FormError(key, s"$key is missing")))

      override def unbind(key: String, value: T): Map[String, String] =
        Map(key -> asString(value))
