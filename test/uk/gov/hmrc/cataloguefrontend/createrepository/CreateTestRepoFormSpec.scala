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

package uk.gov.hmrc.cataloguefrontend.createrepository

import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class CreateTestRepoFormSpec extends UnitSpec {

  "repoNameTestConstraint" when {
    "the repo name should end in 'test' or 'tests'" should {
      "return false" in {
        CreateTestRepoForm.repoNameTestConstraint(
          CreateServiceRepoForm(
            repositoryName = "test-service",
            makePrivate    = false,
            teamName       = TeamName("test"),
            repoType       = CreateTestRepositoryType.UITest.asString
          )
        ) shouldBe false
      }
      "return true" in {
        CreateTestRepoForm.repoNameTestConstraint(
          CreateServiceRepoForm(
            repositoryName = "test-service-ui-tests",
            makePrivate    = false,
            teamName       = TeamName("test"),
            repoType       = CreateTestRepositoryType.UITest.asString
          )
        ) shouldBe true

        CreateTestRepoForm.repoNameTestConstraint(
          CreateServiceRepoForm(
            repositoryName = "test-service-ui-test",
            makePrivate = false,
            teamName = TeamName("test"),
            repoType = CreateTestRepositoryType.UITest.asString
          )
        ) shouldBe true
      }
    }
  }
}
