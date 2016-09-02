/*
 * Copyright 2016 HM Revenue & Customs
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

import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date

import org.scalatest.{Matchers, WordSpec}

class ReleasesFilterSpec extends WordSpec with Matchers {
  val format = new java.text.SimpleDateFormat("dd-MM-yyyy")

  "form" should {
    "bind the form correctly" in {

      val formData: Map[String, String] = Map("serviceName" -> "aService", "from" -> "23-04-2016", "to" -> "25-05-2016")

      ReleasesFilter.form.bind(formData).value shouldBe Some(ReleasesFilter(Some("aService"), Some(format.parse("23-04-2016")), Some(format.parse("25-05-2016"))))

      ReleasesFilter.form.bind(formData - "to").value shouldBe Some(ReleasesFilter(Some("aService"), Some(format.parse("23-04-2016")), None))
      ReleasesFilter.form.bind(formData - "to" - "from").value shouldBe Some(ReleasesFilter(Some("aService"), None, None))
      ReleasesFilter.form.bind(formData - "serviceName" - "to" - "from").value shouldBe Some(ReleasesFilter(None, None, None))
      ReleasesFilter.form.bind(formData + ("serviceName" -> "" )).value shouldBe Some(ReleasesFilter(None, Some(format.parse("23-04-2016")), Some(format.parse("25-05-2016"))))
      ReleasesFilter.form.bind(formData + ("serviceName" -> " " )).value shouldBe Some(ReleasesFilter(None, Some(format.parse("23-04-2016")), Some(format.parse("25-05-2016"))))

    }

    "validate data is of correct format" in {

      val formData: Map[String, String] = Map("serviceName" -> "aService", "from" -> "23-04-2016", "to" -> "25-05-2016")

      ReleasesFilter.form.bind(formData).hasErrors shouldBe false
      
    }

  }

}
