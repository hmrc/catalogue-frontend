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

package uk.gov.hmrc.cataloguefrontend.deployments

import uk.gov.hmrc.cataloguefrontend.test.UnitSpec

import java.time.LocalDate

class DeploymentsFilterSpec extends UnitSpec {
  import DeploymentEventsController._

  val formData: Map[String, String] =
    Map("team" -> "teamA", "serviceName" -> "aService", "from" -> "2016-04-23", "to" -> "2016-05-25")

  "form" should {
    "bind the form with defaults if no filters set" in {
      DeploymentEventsController.form.bind(Map.empty[String, String]).value shouldBe Some(
        SearchForm(defaultFromTime(), defaultToTime(), None, None, None)
      )
    }

    "bind the form with values set" in {
      val formData = Map("from" -> "2020-06-10", "to" -> "2020-06-11", "team" -> "teamA", "service" -> "some-app", "platform" -> "ecs")
      DeploymentEventsController.form.bind(formData).value shouldBe Some(
        SearchForm(from = LocalDate.parse("2020-06-10"), to = LocalDate.parse("2020-06-11"), team = Some("teamA"), service = Some("some-app"), page = None)
      )
    }

    "error if the from date is before the to date" in {
      val formData = Map("from" -> "2020-06-11", "to" -> "2020-06-10")
      DeploymentEventsController.form.bind(formData).errors.flatMap(_.messages) shouldBe List("To Date must be greater than or equal to From Date")
    }

    "error if the from date is the wrong format" in {
      val formData = Map("from" -> "2020/06/11")
      DeploymentEventsController.form.bind(formData).errors.flatMap(_.messages) shouldBe List("error.date")
    }

    "allow setting the to date, defaulting from date" in {
      val formData = Map("to" -> "2099-06-10")
      DeploymentEventsController.form.bind(formData).value shouldBe Some(
        SearchForm(defaultFromTime(), LocalDate.parse("2099-06-10"), None, None, None)
      )
    }

    "allow setting the from date, defaulting to date" in {
      val formData = Map("from" -> "2020-06-10")
      DeploymentEventsController.form.bind(formData).value shouldBe Some(
        SearchForm(LocalDate.parse("2020-06-10"), defaultToTime(), None, None, None)
      )
    }

    "error if the page is set to a negative number" in {
      val formData = Map("from" -> "2020-06-11", "to" -> "2020-06-10", "page" -> "-1")
      DeploymentEventsController.form.bind(formData).errors.flatMap(_.messages) shouldBe List("error.min")
    }
  }
}
