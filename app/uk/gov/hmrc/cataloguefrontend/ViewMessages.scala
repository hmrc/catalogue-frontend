/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.Play
import play.api.i18n.Messages


object ViewMessages {
  val noIndicatorsData = "<p>There's nothing here - this probably means you haven't released anything yet! Get shipping " +
    "to see your data. If you think you're seeing this message in error or have any other feedback, please let us know in " +
    """<a href="https://hmrcdigital.slack.com/messages/team-platops/">#team-platops</a></p>"""

  val indicatorsServiceError = "Sorry about that, there was a problem fetching the indicator data. This will " +
    "hopefully be resolved shortly, but in the meantime feel free to let us know or provide general feedback in " +
    "<a href=\"https://hmrcdigital.slack.com/messages/team-platops/\">#team-platops</a>"


  def noRepoOfType(item : String) = s"This team doesn't have any $item repositories, or our <a href='/#maintenance'>$item repository detection strategy</a> needs " +
    "improving. In case of the latter, let us know in <a href=\"https://hmrcdigital.slack.com/messages/team-platops/\" " +
    "target=\"_blank\">#team-platops</a> on Slack."

  val otherTeamsAre = "Other teams that also have a stake in this service are:"

  val appConfigBaseUrl = Play.current.configuration.getString(s"urlTemplates.app-config-base").getOrElse(throw new IllegalArgumentException("didn't app config base URL configuration"))

  val informationalText = Play.current.configuration.getString(s"info-panel-text").getOrElse(throw new IllegalArgumentException("didn't find info panel configuration"))
}
