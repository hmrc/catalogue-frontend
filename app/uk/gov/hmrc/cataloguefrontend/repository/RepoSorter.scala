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

package uk.gov.hmrc.cataloguefrontend.repository

import akka.util.ByteString
import play.api.mvc.BodyParser
import uk.gov.hmrc.cataloguefrontend.connector.GitRepository

import java.io.{ByteArrayInputStream, InputStream}
import javax.inject.Singleton
import scala.io.Source
import views.html.partials.RepoSearchResultsPage

object RepoSorter {

  def orderings(column: String): Ordering[GitRepository] = column match {
    case "name"             => Ordering.by(_.name.toLowerCase)
    case "status"           => Ordering.by(_.status)
    case "repoType"         => Ordering.by(_.repoType.asString)
    case "teamNames"        => Ordering.by(_.teamNameDisplay)
    case "branchProtection" => Ordering.by(_.branchProtectionEnabled)
    case "createdDate"      => Ordering.by(_.createdDate)
    case "lastActiveDate"   => Ordering.by(_.lastActiveDate)
    case _                  => Ordering.by(_.name.toLowerCase)
  }

  def sort(repos: Seq[GitRepository], column: String, sortOrder: String): Seq[GitRepository] =
   sortOrder match {
      case "desc"            =>  repos.sorted(orderings(column).reverse)
      case _                 =>  repos.sorted(orderings(column))
    }
}