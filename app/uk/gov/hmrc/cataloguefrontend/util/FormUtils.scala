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

import play.api.data.validation.Constraint

trait FormUtils {

  /** Like Forms.nonEmpty, but has no constraint info label */
  def notEmpty = {
    import play.api.data.validation._
    Constraint[String]("") { o =>
      if (o == null || o.trim.isEmpty) Invalid(ValidationError("error.required")) else Valid
    }
  }

  def notEmptySeq = {
    import play.api.data.validation._
    Constraint[Seq[_]]("") { o =>
      if (o == null || o.isEmpty) Invalid(ValidationError("error.required")) else Valid
    }
  }
}

object FormUtils extends FormUtils
