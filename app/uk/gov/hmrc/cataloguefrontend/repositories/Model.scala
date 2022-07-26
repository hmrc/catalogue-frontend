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

package uk.gov.hmrc.cataloguefrontend.repositories

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed trait RepositoryType {
  def asString: String
}

object RepositoryType {

  def parse(s: String): Either[String, RepositoryType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repository type - should be one of: ${values.map(_.asString).mkString(", ")}")

  val values: List[RepositoryType] = List(
    Service,
    Library,
    Other,
    Prototype,
    All
  )

  case object Library extends RepositoryType {
    override val asString: String = "Library"
  }
  case object Other extends RepositoryType {
    override val asString: String = "Other"
  }
  case object Prototype extends RepositoryType {
    override val asString: String = "Prototype"
  }
  case object Service extends RepositoryType {
    override val asString: String = "Service"
  }
  case object All extends RepositoryType {
    override val asString: String = "All"
  }
}
