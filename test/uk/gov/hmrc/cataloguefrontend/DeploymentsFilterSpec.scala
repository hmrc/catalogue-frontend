/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.OptionValues._
import org.scalatest.{Matchers, WordSpec}

class DeploymentsFilterSpec extends WordSpec with Matchers with TypeCheckedTripleEquals {

  implicit def toDateTime(s: String): LocalDateTime = LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()

  val formData: Map[String, String] = Map("team" -> "teamA", "serviceName" -> "aService", "from" -> "2016-04-23", "to" -> "2016-05-25")

  "form" should {
    "bind the form correctly" in {

      DeploymentsFilter.form.bind(formData).value shouldBe Some(DeploymentsFilter(Some("teamA"), Some("aService"), Some("2016-04-23"), Some("2016-05-25")))

      DeploymentsFilter.form.bind(formData - "to").value shouldBe Some(DeploymentsFilter(Some("teamA"), Some("aService"), Some("2016-04-23"), None))
      DeploymentsFilter.form.bind(formData - "to" - "from").value shouldBe Some(DeploymentsFilter(Some("teamA"), Some("aService"), None, None))
      DeploymentsFilter.form.bind(formData - "team" - "serviceName" - "to" - "from").value shouldBe Some(DeploymentsFilter(None, None, None, None))
      DeploymentsFilter.form.bind(formData + ("team" -> "") + ("serviceName" -> "")).value shouldBe Some(DeploymentsFilter(None, None, Some("2016-04-23"), Some("2016-05-25")))
      DeploymentsFilter.form.bind(formData + ("team" -> " ") + ("serviceName" -> " ")).value shouldBe Some(DeploymentsFilter(None, None, Some("2016-04-23"), Some("2016-05-25")))

    }

    "validate date is of correct format (dd-MM-yyyy)" in {

      DeploymentsFilter.form.bind(formData + ("from" -> "23/04/2016")).error("from").value.message should ===("from.error.date")
      DeploymentsFilter.form.bind(formData + ("from" -> "23/54/2016")).error("from").value.message should ===("from.error.date")
      DeploymentsFilter.form.bind(formData + ("to" -> "23/04/2016")).error("to").value.message should ===("to.error.date")
      DeploymentsFilter.form.bind(formData + ("to" -> "23/54/2016")).error("to").value.message should ===("to.error.date")

    }


  }

}
