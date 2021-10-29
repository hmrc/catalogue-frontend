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

package uk.gov.hmrc.cataloguefrontend

import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Dependency}

object ServiceInfoView {
  /*
   * Capture any curated library dependencies from main / Github that are not referenced by the 'latest' slug,
   * and assume that they represent 'test-only' library dependencies.
   */
  def buildToolsFrom(
    optLegacyLatestDependencies: Option[Dependencies],
    librariesOfLatestSlug: Seq[Dependency]
  ): Option[Dependencies] =
    optLegacyLatestDependencies.map { legacyDependencies =>
      val libraryNamesInLatestSlug = librariesOfLatestSlug.map(_.name).toSet
      legacyDependencies.copy(
        libraryDependencies = legacyDependencies.libraryDependencies
          .filterNot { library =>
            libraryNamesInLatestSlug.contains(library.name)
          }
      )
    }
}
