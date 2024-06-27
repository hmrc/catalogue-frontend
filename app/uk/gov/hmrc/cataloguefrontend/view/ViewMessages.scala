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

package uk.gov.hmrc.cataloguefrontend.view

import javax.inject.Inject
import play.api.Configuration

class ViewMessages @Inject()(configuration: Configuration):

  def noRepoOfTypeForTeam(item: String): String =
    s"""This team doesn't have any $item repositories, or our <a href="/#maintenance">$item repository detection strategy</a> needs
      improving. In case of the latter, let us know in
      <a href="https://hmrcdigital.slack.com/messages/team-platops/" target="_blank" rel="noreferrer noopener">#team-platops<span class="glyphicon glyphicon-new-window"/></a>"""

  def noRepoOfTypeForDigitalService(item: String) =
    s"""This digital service doesn't have any $item repositories assigned to it. The <a href=\" / \">home</a> page provides the necessary instructions to make it appear. Reach out in #team-platops on Slack for inquiries."""

  val informationalText: String =
    configuration.get[String](s"info-panel-text")

  val notSpecifiedText: String =
    "Not specified"
