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

package uk.gov.hmrc.cataloguefrontend.util

import uk.gov.hmrc.cataloguefrontend.connector.Link
import uk.gov.hmrc.cataloguefrontend.connector.model.Version

object GithubLink {

  def create(repoName: String, template: String, version1: Version, version2: Version): Link =
    Link(
      name        = repoName
    , displayName = repoName
    , url         = template
                      .replace(s"$${repoName}", UrlUtils.encodePathParam(repoName))
                      .replace(s"$${version1}", UrlUtils.encodePathParam(version1.original))
                      .replace(s"$${version2}", UrlUtils.encodePathParam(version2.original))
    )
}
