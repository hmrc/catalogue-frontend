/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.repositories

import uk.gov.hmrc.cataloguefrontend.connector.GitRepository

object RepoSorter {

  val orderings: Map[String, Ordering[GitRepository]] = Map(
    "name"             -> Ordering.by(_.name.toLowerCase),
    "status"           -> Ordering.by(_.status),
    "repoType"         -> Ordering.by(_.repoType.asString),
    "teamNames"        -> Ordering.by(repo => repo.teamNames match {
      case names if names.length > 4 => s"shared by ${names.length} teams"
      case names if names.isEmpty    => ""
      case names                     => names.minBy(_.toLowerCase()).toLowerCase
    }),
    "branchProtection" -> Ordering.by(_.branchProtectionEnabled),
    "createdDate"      -> Ordering.by(_.createdDate),
    "lastActiveDate"   -> Ordering.by(_.lastActiveDate)
  )

  def sort(repos: Seq[GitRepository], column: String, sortOrder: String): Seq[GitRepository] =
   sortOrder match {
      case "asc"             =>  repos.sorted(orderings(column))
      case "desc"            =>  repos.sorted(orderings(column).reverse)
    }
}
