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

package uk.gov.hmrc.cataloguefrontend.view.partials.html

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, Organisation, RepoType, ServiceType}
import uk.gov.hmrc.cataloguefrontend.cost.Zone
import uk.gov.hmrc.cataloguefrontend.model.DigitalService

import java.time.Instant

class DetailsSpec extends AnyWordSpec with Matchers {

  val repo = GitRepository(
    name           = "reponame",
    organisation   = Some(Organisation.Mdtp),
    description    = "some description",
    createdDate    = Instant.parse("2018-12-31T08:30:30.00Z"),
    lastActiveDate = Instant.parse("2018-12-31T18:30:30.00Z"),
    owningTeams    = Seq(),
    teamNames      = Seq(),
    githubUrl      = "http://url",
    language       = None,
    repoType       = RepoType.Other,
    isPrivate      = true,
    isArchived     = false,
    defaultBranch  = "main",
    serviceType    = Option(ServiceType.Backend),
  )

  val repoWithDigitalServiceGrouping = repo.copy(digitalServiceName = Some(DigitalService("Digital Service Name")))

  private val request = FakeRequest()

  "details" should {
    "display description when available" in {
      val result = details(repo)(request).body
      result should include ("some description")
    }

    "display zone" in {
      val result = details(repo, zone = Some(Zone.Protected))(request).body
      result should include ("id=\"repository-zone\"")
      result should include ("Protected")
    }

    "display repository visibility" in {
      val result = details(repo)(request).body
      result should include ("id=\"repository-visibility\"")
      result should include ("Private")
    }

    "display repository service type" in {
      val result = details(repo)(request).body
      result should include ("id=\"repository-service-type\"")
      result should include ("Backend")
    }

    "should not display description when it is not available" in {
      val result = details(repo.copy(description = ""))(request).body
      result should not include ("some description")
    }

    "display Created At Date" in {
      val result = details(repo)(request).body
      result should include ("id=\"repository-created-at\"")
      result should include ("31 Dec 2018 08:30")
    }

    "display Last Active Date" in {
      val result = details(repo)(request).body
      result should include ("id=\"repository-last-active\"")
      result should include ("31 Dec 2018 18:30")
    }

    "not display Digital Service when not specified" in {
      val result = details(repo)(request).body
      result should not include ("id=\"repository-digital-service-name\"")
      result should not include ("Digital Service Name")
    }

    "display Digital Service when specified" in {
      val result = details(repoWithDigitalServiceGrouping)(request).body
      result should include ("id=\"repository-digital-service-name\"")
      result should include ("Digital Service Name")
    }
  }
}
