/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.costs.model

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.model.Environment.{Development, QA}

class ServiceDeploymentConfigGroupedSpec extends AnyWordSpec {

  "ServiceDeploymentConfigGrouped.totalEstimatedCostFormatted" when {

    "return the sum of estimated costs of slots * instances for each config in currency format" in {

      val environment1 = EnvironmentConfig(Development, 10, 10)
      val environment2 = EnvironmentConfig(QA, 10, 10)

      val result = ServiceDeploymentConfigGrouped("foo", Seq(environment1, environment2))

      // costOfSlotsPerYear * (slots * instances)
      result.totalEstimatedCostFormatted(10.00) shouldBe "2,000"
    }

    "return the sum of estimated costs of slots * instances for each config" in {

      val environment1 = EnvironmentConfig(Development, 10, 10)
      val environment2 = EnvironmentConfig(QA, 10, 10)

      val result = ServiceDeploymentConfigGrouped("foo", Seq(environment1, environment2))

      // costOfSlotsPerYear * (slots * instances)
      result.totalEstimatedCosts(10.00) shouldBe 2000.0
    }

  }

}
