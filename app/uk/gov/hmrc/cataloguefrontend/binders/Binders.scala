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

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.cataloguefrontend.connector.ServiceType

import java.time.{Instant, LocalDate}
import scala.util.Try

object Binders {

  implicit def instantBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[Instant] =
    new QueryStringBindable[Instant] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Instant]] =
        strBinder.bind(key, params)
          .map(_.flatMap(s => Try(Instant.parse(s)).toEither.left.map(_.getMessage)))

      override def unbind(key: String, value: Instant): String =
        strBinder.unbind(key, value.toString)
    }

  implicit def localDateBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[LocalDate] =
    new QueryStringBindable[LocalDate] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDate]] =
        strBinder.bind(key, params)
          .map(_.flatMap(s => Try(LocalDate.parse(s)).toEither.left.map(_.getMessage)))

      override def unbind(key: String, value: LocalDate): String =
        strBinder.unbind(key, value.toString)
    }

  implicit def serviceTypeBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[ServiceType] =
    new QueryStringBindable[ServiceType] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ServiceType]] =
        strBinder.bind(key, params)
          .map(_.flatMap(s => ServiceType.parse(s)))

      override def unbind(key: String, value: ServiceType): String =
        strBinder.unbind(key, value.asString)
    }
}
