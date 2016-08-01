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

object ViewMessages {
  val noIndicatorsData = "There's nothing here - this probably means you haven't released anything yet! Get shipping " +
    "to see your data. If you think you're seeing this message in error or have any other feedback, please let us know in " +
    "<a href=\"https://hmrcdigital.slack.com/messages/team-platops/\">#team-platops</a>"

  val indicatorsServiceError = "Sorry about that, there was a problem fetching the indicator data. This will " +
    "hopefully be resolved shortly, but in the meantime feel free to let us know or provide general feedback in " +
    "<a href=\"https://hmrcdigital.slack.com/messages/team-platops/\">#team-platops</a>"

  val fprExplanationTest = "<p>This indicator shows your progress towards frequent delivery of value-add. It is composed of two metrics:</p>" +
    "<ul><li><strong>Lead Time</strong> - the number of days between creating a release and deploying it to production</li>" +
    "<li><strong>Interval</strong> - the number of days between production deployments</li></ul>" +
    "<p>Each monthly measurement is a 3 month rolling median. Trending towards lower numbers suggests an improvement; an absence of numbers suggests inactivity</p>"

  val noServices = "This team doesn't have any service repositories, or our <a href=\"/#maintenance\">service repository detection strategy</a> needs " +
    "improving. In case of the latter, let us know in <a href=\"https://hmrcdigital.slack.com/messages/team-platops/\" " +
    "target=\"_blank\">#team-platops</a> on Slack."

  val otherTeamsAre = "Other teams that also have a stake in this service are:"

  val appConfigBaseUrl = Play.current.configuration.getString(s"urlTemplates.app-config-base").getOrElse(throw new IllegalArgumentException("didn't app config base URL configuration"))

  val informationalText = Play.current.configuration.getString(s"info-panel-text").getOrElse(throw new IllegalArgumentException("didn't find info panel configuration"))
}
