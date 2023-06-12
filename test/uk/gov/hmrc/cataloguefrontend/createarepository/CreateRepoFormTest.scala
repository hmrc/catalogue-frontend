package uk.gov.hmrc.cataloguefrontend.createarepository

import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class CreateRepoFormTest extends UnitSpec {

  "repoNameWhiteSpaceValidation" when {
    "The repo name contains a space" should {
      "return false" in {
        CreateRepoForm.repoNameWhiteSpaceValidation("hello world") shouldBe false
        CreateRepoForm.repoNameWhiteSpaceValidation(" helloworld") shouldBe false
        CreateRepoForm.repoNameWhiteSpaceValidation("helloworld ") shouldBe false
      }
    }

    "The repo name contains multiple spaces" should {
      "return false" in {
        CreateRepoForm.repoNameWhiteSpaceValidation("   hello world") shouldBe false
        CreateRepoForm.repoNameWhiteSpaceValidation("hello         world") shouldBe false
        CreateRepoForm.repoNameWhiteSpaceValidation("helloworld   ") shouldBe false
      }
    }

    "The repo name contains a tab" should {
      "return false" in {
        CreateRepoForm.repoNameWhiteSpaceValidation("\thello world") shouldBe false
      }
    }

    "The repo name contains a newline" should {
      "return false" in {
        CreateRepoForm.repoNameWhiteSpaceValidation("\nhello world") shouldBe false
      }
    }

    "The repo name contains no whitespace characters" should {
      "return true" in {
        CreateRepoForm.repoNameWhiteSpaceValidation("helloworld") shouldBe true
      }
    }
  }

  "repoNameLengthValidation" when {
    "the repo name contains less than 48 characters" should {
      "return false" in {
        CreateRepoForm.repoNameLengthValidation("helloworld") shouldBe true
      }
    }

    "the repo name contains exactly 47 characters" should {
      "return true" in {
        CreateRepoForm.repoNameLengthValidation("helloworldhelloworldhelloworldhelloworldhellowo") shouldBe true
      }
    }

    "the repo name contains exactly 48 characters" should {
      "return false" in {
        CreateRepoForm.repoNameLengthValidation("helloworldhelloworldhelloworldhelloworldhellowor") shouldBe false
      }
    }

    "the repo name contains more than 48 characters" should {
      "return false" in {
        CreateRepoForm.repoNameLengthValidation("helloworldhelloworldhelloworldhelloworldhelloworldhelloworld") shouldBe false
      }
    }
  }

  "repoNameUnderscoreValidation" when {
    "the repo name contains underscores" should {
      "return false" in {
        CreateRepoForm.repoNameUnderscoreValidation("hello_world") shouldBe false
        CreateRepoForm.repoNameUnderscoreValidation("_helloworld") shouldBe false
        CreateRepoForm.repoNameUnderscoreValidation("helloworld_") shouldBe false
      }
    }

    "the repo name does not contain underscores" should {
      "return true" in {
        CreateRepoForm.repoNameUnderscoreValidation("helloworld") shouldBe true
      }
    }
  }

  "repoNameLowercaseValidation" when {
    "the repo name contains an uppercase char" should {
      "return false" in {
        CreateRepoForm.repoNameLowercaseValidation("hello-worLd") shouldBe false
        CreateRepoForm.repoNameLowercaseValidation("H") shouldBe false
        CreateRepoForm.repoNameLowercaseValidation("HELLO WORLD") shouldBe false
        CreateRepoForm.repoNameLowercaseValidation("helloworlD") shouldBe false
      }
    }

    "the repo name contains no uppercase chars" should {
      "return true" in {
        CreateRepoForm.repoNameLowercaseValidation("hello-world") shouldBe true
      }
    }
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
          CreateRepoForm(
            repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Backend microservice - with mongodb", bootstrapTag = None
          )
        ) shouldBe false

        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(
            repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Backend microservice", bootstrapTag = None
          )
        ) shouldBe false
      }
    }

    "the repo type contains 'backend', and the repo name does not contain frontend" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Backend microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }

    "the repo type doesn't contain backend at all" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation1(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }
  }

  "conflictingFieldsValidation2" when {
    "the repo type contains 'frontend' and the repo name contains 'backend'" should {
      "return false" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(
            repositoryName = "test-service-backend", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
          )
        ) shouldBe false
      }
    }

    "the repo type contains 'frontend' and the repo name does not contain 'backend'" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }

    "the repo type doesn't contain frontend at all" should {
      "return true" in {
        CreateRepoForm.conflictingFieldsValidation2(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }
  }

  "frontendValidation1" when {
    "the repo type contains 'frontend' and the repo name does not contain 'frontend'" should {
      "return false" in {
        CreateRepoForm.frontendValidation1(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
          )
        ) shouldBe false
      }
    }

      "the repo type contains 'frontend' and the repo name contains 'frontend'" should {
        "return true" in {
          CreateRepoForm.frontendValidation1(
            CreateRepoForm(
              repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
            )
          ) shouldBe true

          CreateRepoForm.frontendValidation1(
            CreateRepoForm(
              repositoryName = "test-service-FrOnTeNd", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
            )
          ) shouldBe true
        }
      }

      "the repo type doesn't contain frontend at all" should {
        "return true" in {
          CreateRepoForm.frontendValidation1(
            CreateRepoForm(
              repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "API microservice", bootstrapTag = None
            )
          ) shouldBe true
        }
      }
    }

  "frontendValidation2" when {
    "the repo name contains 'frontend' and the repo type is not frontend" should {
      "return false" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(
            repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "API microservice", bootstrapTag = None
          )
        ) shouldBe false
      }
    }

    "the repo name contains 'frontend' and the repo type is 'frontend'" should {
      "return true" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(
            repositoryName = "test-service-frontend", makePrivate = false, teamName = "test", repoType = "Frontend microservice - with scaffold", bootstrapTag = None
          )
        ) shouldBe true

        CreateRepoForm.frontendValidation2(
          CreateRepoForm(
            repositoryName = "test-service-FrOnTeNd", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }

    "the repo name doesn't contain frontend at all" should {
      "return true" in {
        CreateRepoForm.frontendValidation2(
          CreateRepoForm(
            repositoryName = "test-service", makePrivate = false, teamName = "test", repoType = "Frontend microservice", bootstrapTag = None
          )
        ) shouldBe true
      }
    }
  }





}
