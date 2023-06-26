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

import uk.gov.hmrc.cataloguefrontend.util.{Enum, WithAsString}

sealed trait ValueFilterType extends WithAsString {val displayString: String;}

object ValueFilterType extends Enum[ValueFilterType] {

  case object Contains       extends ValueFilterType { val asString = "contains";       val displayString = "Contains"         }
  case object DoesNotContain extends ValueFilterType { val asString = "doesNotContain"; val displayString = "Does not contain" }
  case object EqualTo        extends ValueFilterType { val asString = "equalTo";        val displayString = "Equal to"         }
  case object NotEqualTo     extends ValueFilterType { val asString = "notEqualTo";     val displayString = "Not Equal to"     }
  case object IsEmpty        extends ValueFilterType { val asString = "isEmpty";        val displayString = "Is Empty"         }

  override val values: List[ValueFilterType] =
    List(Contains, DoesNotContain, EqualTo, NotEqualTo, IsEmpty)
}

sealed trait GroupBy extends WithAsString {val displayString: String;}

object GroupBy extends Enum[GroupBy] {

  case object Key     extends GroupBy { val asString = "key";     val displayString = "Key"     }
  case object Service extends GroupBy { val asString = "service"; val displayString = "Service" }

  override val values: List[GroupBy] =
    List(Key, Service)
}

sealed trait ServiceType extends WithAsString {val displayString: String;}

object ServiceType extends Enum[ServiceType] {

  case object Frontend extends ServiceType { val asString = "FrontendService"; val displayString = "Frontend"     }
  case object Backend  extends ServiceType { val asString = "BackendService";  val displayString = "Backend" }

  override val values: List[ServiceType] =
    List(Frontend, Backend)
}