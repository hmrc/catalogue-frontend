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

import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class CreateTestRepoFormSpec extends UnitSpec {

  "conflictingFieldsValidationUiTests" when {
    "the repo type starts with 'ui' the repo name should end with '-ui-tests'" should {
      "return false" in {
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service-api-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithScalaTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithScalaTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service-api-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithCucumber.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithCucumber.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-serviceui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithScalaTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-serviceui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithCucumber.asString)
        ) shouldBe false
      }
      "return true" in {
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service-ui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithScalaTest.asString)
        ) shouldBe true
        CreateTestRepoForm.conflictingFieldsValidationUiTests(
          CreateServiceRepoForm(repositoryName = "test-service-ui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.uiTestWithCucumber.asString)
        ) shouldBe true
      }
    }
  }

  "conflictingFieldsValidationApiTests" when {
    "the repo type starts with 'api' the repo name should end with '-api-tests'" should {
      "return false" in {
        CreateTestRepoForm.conflictingFieldsValidationApiTests(
          CreateServiceRepoForm(repositoryName = "test-service-ui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.apiTestWithScalaTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationApiTests(
          CreateServiceRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.apiTestWithScalaTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationApiTests(
          CreateServiceRepoForm(repositoryName = "test-serviceapi-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.apiTestWithScalaTest.asString)
        ) shouldBe false
      }
      "return true" in {
        CreateTestRepoForm.conflictingFieldsValidationApiTests(
          CreateServiceRepoForm(repositoryName = "test-service-api-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.apiTestWithScalaTest.asString)
        ) shouldBe true
      }
    }
  }

  "conflictingFieldsValidationPerformanceTests" when {
    "the repo type starts with 'performance' the repo name should end with '-performance-tests'" should {
      "return false" in {
        CreateTestRepoForm.conflictingFieldsValidationPerformanceTests(
          CreateServiceRepoForm(repositoryName = "test-service-ui-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.performanceTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationPerformanceTests(
          CreateServiceRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.performanceTest.asString)
        ) shouldBe false
        CreateTestRepoForm.conflictingFieldsValidationPerformanceTests(
          CreateServiceRepoForm(repositoryName = "test-serviceperformance-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.performanceTest.asString)
        ) shouldBe false
      }
      "return true" in {
        CreateTestRepoForm.conflictingFieldsValidationPerformanceTests(
          CreateServiceRepoForm(repositoryName = "test-service-performance-tests", makePrivate = false, teamName = "test", repoType = CreateTestRepositoryType.performanceTest.asString)
        ) shouldBe true
      }
    }
  }
}
