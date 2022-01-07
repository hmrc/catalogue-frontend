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

package uk.gov.hmrc.cataloguefrontend

import javax.inject.Inject
import play.api.Configuration
import play.twirl.api.Html

class ViewMessages @Inject() (configuration: Configuration) {
  val noJobExecutionData: String = "<p>It's possible that there is no Jenkins job set up for this repository, or a job exists " +
    "but there is no past build data available. If you think you're seeing this message in error or have any other feedback, " +
    "please let us know in " +
    """<a href="https://hmrcdigital.slack.com/messages/team-platops/" target="_blank">#team-platops<span class="glyphicon glyphicon-new-window"/></a></p>"""

  def noRepoOfTypeForTeam(item: String) =
    s"This team doesn't have any $item repositories, or our <a href='/#maintenance'>$item repository detection strategy</a> needs " +
      "improving. In case of the latter, let us know in <a href=\"https://hmrcdigital.slack.com/messages/team-platops/\" " +
      "target=\"_blank\">#team-platops<span class=\"glyphicon glyphicon-new-window\"/></a> on Slack."

  def noRepoOfTypeForDigitalService(item: String) =
    s"""This digital service doesn't have any $item repositories assigned to it. The <a href=\" / \">home</a> page provides the necessary instructions to make it appear. Reach out in #team-platops on Slack for inquiries."""

  val otherTeamsAre = "Other teams that also have a stake in this service are:"

  val informationalText: String = configuration.get[String](s"info-panel-text")

  def noJobExecutionTimeDataHtml =
    Html(s"""<h2 class="chart-message text-center">No data to show</h2>""" + s"<p>$noJobExecutionData</p>")

  val notSpecifiedText = "Not specified"

  val prototypesBaseUrl: String = configuration.get[String](s"prototypes-base-url")
}
