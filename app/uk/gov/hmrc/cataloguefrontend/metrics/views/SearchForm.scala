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

package uk.gov.hmrc.cataloguefrontend.metrics.views

import uk.gov.hmrc.cataloguefrontend.metrics.model.{DependencyName, GroupName, RepositoryName}

final case class SearchForm(
                             group: Option[GroupName],
                             dependency: Option[DependencyName],
                             repository: Option[RepositoryName]
                           )

object SearchForm {
  def applyRaw(
                group: Option[String],
                dependency: Option[String],
                repository: Option[String]
              ): SearchForm = SearchForm.apply(
    group.map(GroupName.apply),
    dependency.map(DependencyName.apply),
    repository.map(RepositoryName.apply)
  )

  def unapplyRaw(searchForm: SearchForm): Option[(Option[String], Option[String], Option[String])] = unapply(searchForm).map{ case(g, d, r) =>
    (g.map(_.value), d.map(_.value), r.map(_.value))
  }
}
