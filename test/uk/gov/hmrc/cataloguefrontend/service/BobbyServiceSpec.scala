/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time._

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyRuleFactory.aBobbyRule
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyRuleSet
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BobbyServiceSpec extends UnitSpec with MockitoSugar {

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private val connector = mock[ConfigConnector]
  private val today = LocalDate.of(2000, Month.JANUARY, 1)
  private val now = LocalDateTime.of(today, LocalTime.of(1,1,1))
  private val fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneId.systemDefault())
  private val clock = mock[Clock]
  when(clock.instant()).thenReturn(fixedClock.instant())
  when(clock.getZone).thenReturn(fixedClock.getZone)
  private val service = new BobbyService(connector, clock)

  "getRules" should {

    "split rules into upcoming dependencies if from date is later than today" in {

      val futureLibraryRule = aBobbyRule(name = "future.library", from = today.plusDays(1))
      val futurePluginRule = aBobbyRule(name = "future.plugin", from = today.plusDays(1))
      when(connector.bobbyRules()).thenReturn(Future(new BobbyRuleSet(libraries = Seq(futureLibraryRule, aBobbyRule(from = today), aBobbyRule(from = today.minusDays(1))),
        plugins = Seq(futurePluginRule, aBobbyRule(from = today), aBobbyRule(name = "past.plugin", from = today.minusDays(1))))))

      val result = service.getRules.futureValue

      result.upcoming.libraries.length should be (1)
      result.upcoming.libraries should contain (futureLibraryRule)
      result.upcoming.plugins.length should be (1)
      result.upcoming.plugins should contain (futurePluginRule)
    }

    "split rules into active dependencies if from date is not later than today" in {

      val currentLibraryRule = aBobbyRule(name = "current.library", from = today)
      val pastLibraryRule = aBobbyRule(name = "past.library", from = today.minusDays(1))
      val currentPluginRule = aBobbyRule(name = "current.plugin", from = today)
      val pastPluginRule = aBobbyRule(name = "past.plugin", from = today.minusDays(1))
      when(connector.bobbyRules()).thenReturn(Future(new BobbyRuleSet(libraries = Seq(aBobbyRule(from = today.plusDays(1)), currentLibraryRule, pastLibraryRule),
        plugins = Seq(aBobbyRule(from = today.plusDays(1)), currentPluginRule, pastPluginRule))))

      val result = service.getRules.futureValue

      result.active.libraries.length should be (2)
      result.active.libraries should contain (currentLibraryRule)
      result.active.libraries should contain (pastLibraryRule)
      result.active.plugins.length should be (2)
      result.active.plugins should contain (currentPluginRule)
      result.active.plugins should contain (pastPluginRule)
    }

    "sort upcoming rules by date, starting with closest to today's date first" in {

      val expectedRule = aBobbyRule(organisation = "b", from = today.plusDays(1))
      val rules = Seq(aBobbyRule(organisation = "a", from = today.plusDays(2)),
        expectedRule,
        aBobbyRule(organisation = "a", from = today.minusDays(2)),
        aBobbyRule(organisation = "b", from = today.minusDays(1)))
      when(connector.bobbyRules()).thenReturn(Future(new BobbyRuleSet(rules, rules)))

      val result = service.getRules.futureValue

      result.upcoming.libraries.head should be (expectedRule)
      result.upcoming.plugins.head should be (expectedRule)
    }

    "sort active rules by date, starting with closest to today's date first" in {

      val expectedRule = aBobbyRule(organisation = "b", from = today.minusDays(1))
      val rules = Seq(aBobbyRule(organisation = "a", from = today.plusDays(2)),
        aBobbyRule(organisation = "b", from = today.plusDays(1)),
        aBobbyRule(organisation = "b", from = today.minusDays(2)),
        expectedRule)
      when(connector.bobbyRules()).thenReturn(Future(new BobbyRuleSet(rules, rules)))

      val result = service.getRules.futureValue

      result.active.libraries.head should be (expectedRule)
      result.active.plugins.head should be (expectedRule)
    }

    "sort by artifact group/name in ascending order" in {

      val upcomingDate = today.plusDays(1)
      val activeDate = today.minusDays(1)
      val expectedUpcomingRule = aBobbyRule(organisation = "*", name = "b", from = upcomingDate)
      val expectedActiveRule = aBobbyRule(organisation = "*", name = "b", from = activeDate)
      val rules = Seq(aBobbyRule(organisation = "b", name = "a", from = upcomingDate), expectedUpcomingRule, aBobbyRule(organisation = "b", name = "a", from = activeDate), expectedActiveRule)
      when(connector.bobbyRules()).thenReturn(Future(new BobbyRuleSet(rules, rules)))

      val result = service.getRules.futureValue

      result.upcoming.libraries.head should be (expectedUpcomingRule)
      result.upcoming.plugins.head should be (expectedUpcomingRule)
      result.active.libraries.head should be (expectedActiveRule)
      result.active.plugins.head should be (expectedActiveRule)
    }
  }
}
