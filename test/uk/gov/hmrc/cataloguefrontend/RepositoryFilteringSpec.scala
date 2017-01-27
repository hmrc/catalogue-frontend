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

import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.SearchFiltering._

class RepositoryResultSpec extends WordSpec with Matchers {


  "RepositoryFiltering" should {

    "get repositories filtered by only repository name" in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))

      repositories.filter(RepoListFilter(name = Some("serv1"))) shouldBe Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other))

    }

    "get repositories filtered by only repository type" in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))

      repositories.filter(RepoListFilter(repoType = Some("Other"))) shouldBe Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other))
    }

    "get repositories filtered repository type using 'service' as type " in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Other))

      repositories.filter(RepoListFilter(repoType = Some("service"))) shouldBe Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))
    }

    "get repositories filtered repository type using 'deployable' as type " in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Other))

      repositories.filter(RepoListFilter(repoType = Some("Deployable"))) shouldBe Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))
    }


    "get repositories filtered by both name and repository type" in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv4", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))

      repositories.filter(RepoListFilter(name = Some("serv1"),repoType = Some("Other"))) shouldBe Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other))
    }

    "get all repositories (no filter)" in {

      val now = LocalDateTime.now()

      val repositories = Seq(
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv2", createdAt = now, lastUpdatedAt = now, RepoType.Deployable),
        RepositoryDisplayDetails("serv1", createdAt = now, lastUpdatedAt = now, RepoType.Other),
        RepositoryDisplayDetails("serv3", createdAt = now, lastUpdatedAt = now, RepoType.Deployable))

      repositories.filter(RepoListFilter()) shouldBe repositories
    }

  }

}
