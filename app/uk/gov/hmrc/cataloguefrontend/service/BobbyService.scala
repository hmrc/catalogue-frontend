/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyRule, BobbyRuleSet}
import uk.gov.hmrc.cataloguefrontend.service.model.BobbyRulesView
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class BobbyService @Inject()(configConnector: ConfigConnector, clock: Clock)(implicit val ec: ExecutionContext) {

  def getRules()(implicit hc: HeaderCarrier) : Future[BobbyRulesView] = {
    val today = LocalDate.now(clock)
    def filterUpcoming(rule: BobbyRule) = rule.from.isAfter(today)
    def filterActive(rule: BobbyRule) = rule.from.isBefore(today) || rule.from.isEqual(today)
    def compareUpcoming(rule1: BobbyRule, rule2: BobbyRule) = compareRules(rule1, rule2, (date1, date2) => (date1 compareTo date2) < 0)
    def compareActive(rule1: BobbyRule, rule2: BobbyRule) = compareRules(rule1, rule2, (date1, date2) => (date1 compareTo date2) > 0)
    def compareRules(x: BobbyRule, y: BobbyRule, compareDates: (LocalDate, LocalDate) => Boolean) = {
      if (x.from == y.from) (x.groupArtifactName compare y.groupArtifactName) < 0
      else compareDates(x.from, y.from)
    }
    configConnector.bobbyRules().map(r => BobbyRulesView(
        upcoming = BobbyRuleSet(libraries = r.libraries.filter(filterUpcoming).sortWith(compareUpcoming), plugins = r.plugins.filter(filterUpcoming).sortWith(compareUpcoming)),
        active = BobbyRuleSet(libraries = r.libraries.filter(filterActive).sortWith(compareActive), plugins = r.plugins.filter(filterActive).sortWith(compareActive)))
    )
  }

}
