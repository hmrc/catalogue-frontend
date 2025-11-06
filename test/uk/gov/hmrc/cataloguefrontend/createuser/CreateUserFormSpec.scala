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

package uk.gov.hmrc.cataloguefrontend.createuser

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.validation.{Invalid, Valid}
import uk.gov.hmrc.cataloguefrontend.users.CreateUserConstraints

class CreateUserFormSpec extends AnyWordSpec with Matchers {

  "CreateUserConstraints" should {
    "return false when name length is less than 2 characters and greater than 30 characters" in {
      val name = (for (_ <- 0 to 30) yield 'a').mkString

      isNameValid("")  shouldBe false
      isNameValid("a") shouldBe false
      isNameValid(" ") shouldBe false
      isNameValid(name)       shouldBe false
    }

    "return true when name length is greater than 2 characters and less than 31 characters" in {
      val name = (for (_ <- 0 to 29) yield 'a').mkString

      isNameValid("ab") shouldBe true
      isNameValid(name)        shouldBe true
    }

    "return true when there is white space in the name" in {
      isNameValid("test user")  shouldBe true
      isNameValid(" testuser")  shouldBe true
      isNameValid("testuser ")  shouldBe true
      isNameValid("testuser  ") shouldBe true
    }

    "return true when no white space in the name" in {
      isNameValid("testuser") shouldBe true
    }

    "return true when name contains an underscore" in {
      isNameValid("test_user") shouldBe true
      isNameValid("testuser_") shouldBe true
      isNameValid("_testuser") shouldBe true
    }

    "return Invalid when the name contains the word 'service'" in {
      CreateUserConstraints.containsServiceConstraint("service-user") shouldBe Invalid("Should not contain 'service' - if you are trying to create a non human user, please use <a href=\"/create-service-user\"'>Create A Service Account</a> instead")
    }

    "return Invalid when non-service account contact email is a HMRC Digital email" in {
      CreateUserConstraints.contactEmailConstraint(isServiceAccount = false)("test.user@digital.hmrc.gov.uk") shouldBe Invalid("Cannot be a digital email such as: digital.hmrc.gov.uk")
    }
    
    "return Invalid when service account contact email is not a HMRC Digital email" in {
      CreateUserConstraints.contactEmailConstraint(isServiceAccount = true)("test.user@hmrc.gov.uk") shouldBe Invalid("Must be HMRC Digital e.g. email@digital.hmrc.gov.uk")
    }
  }

  private def isNameValid(name: String) =
    CreateUserConstraints.nameConstraints("givenName")
    .forall(c => c(name) == Valid)
}
