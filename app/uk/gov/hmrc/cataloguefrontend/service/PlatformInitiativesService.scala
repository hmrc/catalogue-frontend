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

package uk.gov.hmrc.cataloguefrontend.service

import laika.api.Transformer
import laika.format.Markdown
import laika.markdown.github.GitHubFlavor

class PlatformInitiativesService {
  def markdown(s: String): String = {
    val transformer = Transformer
      .from(Markdown)
      .to(laika.format.HTML)
      .using(GitHubFlavor)
      .build

    transformer.transform(s.take(s.length).mkString).getOrElse("<Unable to render at this time>")
  }
}
