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

package uk.gov.hmrc.cataloguefrontend.createarepository

import play.api.data.validation.Invalid
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class CreateRepoFormSpec extends UnitSpec {

  "repoNameWhiteSpaceValidation" when {
    "The repo name contains a space" should {
      "return false" in {
        isRepoNameValid("hello world", 47) shouldBe false
        isRepoNameValid(" helloworld", 47) shouldBe false
        isRepoNameValid("helloworld ", 47) shouldBe false
      }
    }

    "The repo name contains multiple spaces" should {
      "return false" in {
        isRepoNameValid("   hello world", 47) shouldBe false
        isRepoNameValid("hello         world", 47) shouldBe false
        isRepoNameValid("helloworld    ", 47) shouldBe false
      }
    }

    "The repo name contains a tab" should {
      "return false" in {
        isRepoNameValid("\thello world", 47) shouldBe false
      }
    }

    "The repo name contains a newline" should {
      "return false" in {
        isRepoNameValid("\nhello world", 47) shouldBe false
      }
    }

    "The repo name contains no whitespace characters" should {
      "return true" in {
        isRepoNameValid("helloworld", 47) shouldBe true
      }
    }
  }

  "repoNameLengthValidation" when {
    "the repo name contains less than the allowed number of characters" should {
      "return false" in {
        isRepoNameValid("helloworld", 47) shouldBe true
        isRepoNameValid("helloworld", 30) shouldBe true
      }
    }

    "the repo name contains exactly the allowed number of characters" should {
      "return true" in {
        isRepoNameValid("helloworldhelloworldhelloworldhelloworldhellowo", 47) shouldBe true
        isRepoNameValid("helloworldhelloworldhelloworld", 30) shouldBe true
      }
    }

    "the repo name contains exactly one more than the allowed number of characters" should {
      "return false" in {
        isRepoNameValid("helloworldhelloworldhelloworldhelloworldhellowor", 47) shouldBe false
        isRepoNameValid("helloworldhelloworldhelloworldh", 30) shouldBe false
      }
    }

    "the repo name contains significantly more than 48 characters" should {
      "return false" in {
        isRepoNameValid("helloworldhelloworldhelloworldhelloworldhelloworldhelloworld", 47) shouldBe false
        isRepoNameValid("helloworldhelloworldhelloworldhelloworld", 30) shouldBe false
      }
    }
  }

  "repoNameUnderscoreValidation" when {
    "the repo name contains underscores" should {
      "return false" in {
        isRepoNameValid("hello_world", 47) shouldBe false
        isRepoNameValid("_helloworld", 47) shouldBe false
        isRepoNameValid("helloworld_", 47) shouldBe false
      }
    }

    "the repo name does not contain underscores" should {
      "return true" in {
        isRepoNameValid("helloworld", 47) shouldBe true
      }
    }
  }

  "repoNameLowercaseValidation" when {
    "the repo name contains an uppercase char" should {
      "return false" in {
        isRepoNameValid("hello-worLd", 47) shouldBe false
        isRepoNameValid("H", 47) shouldBe false
        isRepoNameValid("HELLO WORLD", 47) shouldBe false
        isRepoNameValid("helloworlD", 47) shouldBe false
      }
    }

    "the repo name contains no uppercase chars" should {
      "return true" in {
        isRepoNameValid("hello-world", 47) shouldBe true
      }
    }
  }

  private def isRepoNameValid(repoName: String, length: Int) = {
    val constraints = CreateRepoForm.createRepoNameConstraints(length)
    !constraints.map(c => c(repoName)).exists(p => p.isInstanceOf[Invalid])
  }

  "repoTypeValidation" when {
    "the repo type is invalid" should {
      "return false" in {
        CreateRepoForm.repoTypeValidation("frontend") shouldBe false
        CreateRepoForm.repoTypeValidation("backend-service") shouldBe false
      }
    }

    "the repo type is valid" should {
      "return true" in {
        CreateRepoForm.repoTypeValidation("Empty") shouldBe true
        CreateRepoForm.repoTypeValidation("Frontend microservice") shouldBe true
        CreateRepoForm.repoTypeValidation("Frontend microservice - with scaffold") shouldBe true
        CreateRepoForm.repoTypeValidation("Frontend microservice - with mongodb") shouldBe true
        CreateRepoForm.repoTypeValidation("Backend microservice") shouldBe true
        CreateRepoForm.repoTypeValidation("Backend microservice - with mongodb") shouldBe true
        CreateRepoForm.repoTypeValidation("API microservice") shouldBe true
        CreateRepoForm.repoTypeValidation("API microservice - with mongodb") shouldBe true

      }
    }
  }

  "conflictingFieldsValidation1" when {
    "the repo type contains 'backend' and the repo name contains 'frontend'" should {
      "return false" in {
        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Backend microservice - with mongodb")
        ) shouldBe false

        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Backend microservice")
        ) shouldBe false
      }
    }

    "the repo type contains 'backend', and the repo name does not contain frontend" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Backend microservice")
        ) shouldBe true
      }
    }

    "the repo type doesn't contain backend at all" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice")
        ) shouldBe true
      }
    }
  }

  "conflictingFieldsValidation2" when {
    "the repo type contains 'frontend' and the repo name contains 'backend'" should {
      "return false" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(repositoryName = "test-service-backend", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
        ) shouldBe false
      }
    }

    "the repo type contains 'frontend' and the repo name does not contain 'backend'" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
        ) shouldBe true
      }
    }

    "the repo type doesn't contain frontend at all" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice")
        ) shouldBe true
      }
    }
  }

  "frontendValidation1" when {
    "the repo type contains 'frontend' and the repo name does not contain 'frontend'" should {
      "return false" in {
        CreateRepoForm.frontendValidation1(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
        ) shouldBe false
      }
    }

      "the repo type contains 'frontend' and the repo name contains 'frontend'" should {
        "return true" in {
          CreateRepoForm.frontendValidation1(
            CreateRepoForm(repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
          ) shouldBe true

          CreateRepoForm.frontendValidation1(
            CreateRepoForm(repositoryName = "test-service-FrOnTeNd", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
          ) shouldBe true
        }
      }

      "the repo type doesn't contain frontend at all" should {
        "return true" in {
          CreateRepoForm.frontendValidation1(
            CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice")
          ) shouldBe true
        }
      }
    }

  "frontendValidation2" when {
    "the repo name contains 'frontend' and the repo type is not frontend" should {
      "return false" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "API microservice")
        ) shouldBe false
      }
    }

    "the repo name contains 'frontend' and the repo type is 'frontend'" should {
      "return true" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Frontend microservice - with scaffold")
        ) shouldBe true

        CreateRepoForm.frontendValidation2(
          CreateRepoForm(repositoryName = "test-service-FrOnTeNd", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
        ) shouldBe true
      }
    }

    "the repo name doesn't contain frontend at all" should {
      "return true" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice")
        ) shouldBe true
      }
    }
  }
}
