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

package uk.gov.hmrc.cataloguefrontend.bobby

import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyRule, BobbyRuleSet}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.service.model.BobbyRulesView
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.atStartOfDayInstant
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

class BobbyService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector
, clock                  : Clock
)(using ExecutionContext):

  def getRules()(using HeaderCarrier): Future[BobbyRulesView] =
    def ordering(dateAscending: Boolean) =
      Ordering.by: (br: BobbyRule) =>
        ( br.from.atStartOfDayInstant.toEpochMilli.pipe(x => if dateAscending then x else -x)
        , br.group
        , br.artefact
        )

    val today = LocalDate.now(clock)
    serviceConfigsConnector
      .bobbyRules()
      .map: ruleset =>
        val (upcomingLibraries, activeLibraries) = ruleset.libraries.partition(_.from.isAfter(today))
        val (upcomingPlugins  , activePlugins  ) = ruleset.plugins  .partition(_.from.isAfter(today))
        BobbyRulesView(
          upcoming = { given Ordering[BobbyRule] = ordering(dateAscending = true)
                       BobbyRuleSet(upcomingLibraries.sorted, upcomingPlugins.sorted)
                     }
        , active   = { given Ordering[BobbyRule] = ordering(dateAscending = false)
                       BobbyRuleSet(activeLibraries.sorted, activePlugins.sorted)
                     }
        )
  end getRules

end BobbyService
