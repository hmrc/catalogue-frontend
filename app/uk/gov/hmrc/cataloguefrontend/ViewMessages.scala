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

import javax.inject.Inject
import play.api.Configuration
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.connector.RepoType

class ViewMessages @Inject()(configuration: Configuration) {
  val noJobExecutionData: String = "<p>It's possible that there is no Jenkins job set up for this repository, or a job exists " +
    "but there is no past build data available. If you think you're seeing this message in error or have any other feedback, " +
    "please let us know in " +
    """<a href="https://hmrcdigital.slack.com/messages/team-platops/" target="_blank">#team-platops<span class="glyphicon glyphicon-new-window"/></a></p>"""

  val dependenciesText =
    """<p>This report shows the platform dependencies the code in your repository has, what version they are currently at, and highlights if any later versions are available. You should act quickly to rectify the issue when you see a minor version discrepancy, and immediately if you see a major version discrepancy.</p>
                            <p/>
                            <p>You should also monitor the <a href="https://hmrcdigital.slack.com/messages/C04RY81QK" target="_blank">#announcements<span class="glyphicon glyphicon-new-window"/></a> channel for details of any upgrades that may be more involved than simply bumping a version number</p>"""

  val curatedLibsText =
    """<p>Click <a href="https://github.com/hmrc/service-dependencies/blob/master/conf/dependency-versions-config.json" target="_blank">here<span class="glyphicon glyphicon-new-window"/></a> to see the platform libraries that are included in the dependency analysis</p>"""

  def noRepoOfTypeForTeam(item: String) =
    s"This team doesn't have any $item repositories, or our <a href='/#maintenance'>$item repository detection strategy</a> needs " +
      "improving. In case of the latter, let us know in <a href=\"https://hmrcdigital.slack.com/messages/team-platops/\" " +
      "target=\"_blank\">#team-platops<span class=\"glyphicon glyphicon-new-window\"/></a> on Slack."

  def noRepoOfTypeForDigitalService(item: String) =
    s"""This digital service doesn't have any $item repositories assigned to it. The <a href=\" / \">home</a> page provides the necessary instructions to make it appear. Reach out in #team-platops on Slack for inquiries."""

  val otherTeamsAre = "Other teams that also have a stake in this service are:"

  val appConfigBaseUrl: String = configuration.get[String](s"urlTemplates.app-config-base")

  val informationalText: String = configuration.get[String](s"info-panel-text")

  def noJobExecutionTimeDataHtml =
    Html(
      s"""<h2 class="chart-message text-center">No data to show</h2>""" + s"<p>$noJobExecutionData</p>")

  def toTypeText(repoType: RepoType.RepoType): String =
    repoType match {
      case RepoType.Service => "Service"
      case t                => t.toString
    }

  val notSpecifiedText = "Not specified"

  val prototypesBaseUrl: String = configuration.get[String](s"prototypes-base-url")
}
