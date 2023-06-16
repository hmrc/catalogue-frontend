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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import play.api.data.format.Formatter
import play.api.data.FormError

sealed trait ValueFilterType {val asString: String; val displayString: String; }

object ValueFilterType {
  case object Contains       extends ValueFilterType { val asString = "contains";       val displayString = "Contains"         }
  case object DoesNotContain extends ValueFilterType { val asString = "doesNotContain"; val displayString = "Does not contain" }
  case object EqualTo        extends ValueFilterType { val asString = "equalTo";        val displayString = "Equal to"         }
  case object NotEqualTo     extends ValueFilterType { val asString = "notEqualTo";     val displayString = "Not Equal to"     }
  case object IsEmpty        extends ValueFilterType { val asString = "isEmpty";        val displayString = "Is Empty"         }


  val values: List[ValueFilterType]             = List(Contains, DoesNotContain, EqualTo, NotEqualTo, IsEmpty)
  def parse(s: String): Option[ValueFilterType] = values.find(_.asString == s)

  def formFormat: Formatter[ValueFilterType] = new Formatter[ValueFilterType] {
  override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ValueFilterType] =
    data
      .get(key)
      .flatMap(ValueFilterType.parse)
      .fold[Either[Seq[FormError], ValueFilterType]](Left(Seq(FormError(key, "Invalid filter type"))))(Right.apply)

  override def unbind(key: String, value: ValueFilterType): Map[String, String] =
    Map(key -> value.asString)
  }
}

sealed trait GroupBy {val asString: String; val displayString: String; }

object GroupBy {
  case object Key     extends GroupBy { val asString = "key";     val displayString = "Key"     }
  case object Service extends GroupBy { val asString = "service"; val displayString = "Service" }

  val values: List[GroupBy]             = List(Key, Service)
  def parse(s: String): Option[GroupBy] = values.find(_.asString == s)

  def formFormat: Formatter[GroupBy] = new Formatter[GroupBy] {
  override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], GroupBy] =
    data
      .get(key)
      .flatMap(GroupBy.parse)
      .fold[Either[Seq[FormError], GroupBy]](Left(Seq(FormError(key, "Invalid filter type"))))(Right.apply)

  override def unbind(key: String, value: GroupBy): Map[String, String] =
    Map(key -> value.asString)
  }
}
