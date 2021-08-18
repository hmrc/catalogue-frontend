/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.metrics.model

//todo: should it be called DependencyGroup or similar?
case class Group(name: GroupName, repos: Seq[RepositoryName], dependencies: Seq[DependencyName])

object Group{

  def apply(metrics: Seq[ServiceProgressMetrics]): Seq[Group] = metrics
    .groupBy(_.group)
    .mapValues { metrics =>
        val repoNames = metrics.map(_.repository)
          .distinct
          .map(RepositoryName.apply)
        val depNames = metrics.map(_.name)
          .distinct
          .map(DependencyName.apply)
      (repoNames, depNames)
    }
    .map{ case (g, (repositoryNames, depNames)) =>
      Group(GroupName(g), repositoryNames, depNames)
    }
    .toSeq
}
