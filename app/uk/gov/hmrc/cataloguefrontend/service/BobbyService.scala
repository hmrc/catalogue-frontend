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

package uk.gov.hmrc.cataloguefrontend.service

import java.time.{Clock, LocalDate}

import javax.inject.Inject
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyRule, BobbyRuleSet}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.service.model.BobbyRulesView
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class BobbyService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector
, clock                  : Clock
)(implicit val ec: ExecutionContext) {

  def getRules()(implicit hc: HeaderCarrier): Future[BobbyRulesView] = {
    val today = LocalDate.now(clock)

    def sort(ruleset: BobbyRuleSet, sortDateLt: (LocalDate, LocalDate) => Boolean) = {
      def sortRulesLt(x: BobbyRule, y: BobbyRule) =
        if (x.from == y.from)
          if (x.group == y.group) x.artefact < y.artefact
          else x.group < y.group
        else sortDateLt(x.from, y.from)

      BobbyRuleSet(
        libraries = ruleset.libraries.sortWith(sortRulesLt),
        plugins = ruleset.plugins.sortWith(sortRulesLt)
      )
    }

    serviceConfigsConnector
      .bobbyRules()
      .map { ruleset =>
        val (upcomingLibraries, activeLibraries) = ruleset.libraries.partition(_.from isAfter today)
        val (upcomingPlugins, activePlugins)     = ruleset.plugins.partition(_.from isAfter today)
        BobbyRulesView(
          upcoming = sort(BobbyRuleSet(upcomingLibraries, upcomingPlugins), _ isBefore _),
          active = sort(BobbyRuleSet(activeLibraries, activePlugins), _ isAfter _)
        )
      }
  }
}
