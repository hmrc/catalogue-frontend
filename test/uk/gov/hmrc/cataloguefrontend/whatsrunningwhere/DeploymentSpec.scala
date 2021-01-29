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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.Platform.Heritage

class DeploymentSpec extends AnyFlatSpec with Matchers {

  "LastDeployer" should "select the most recent deployer" in {

    val now = Instant.now()
    val h = Deployment(
      Heritage,
      ServiceName("foo"),
      Production,
      VersionNumber("1.1.1"),
      Seq.empty,
      TimeSeen(now),
      TimeSeen(now),
      Seq(
        DeployerAudit("usera", TimeSeen(now.minus(3, ChronoUnit.DAYS))),
        DeployerAudit("userb", TimeSeen(now.minus(1, ChronoUnit.DAYS))),
        DeployerAudit("userc", TimeSeen(now.minus(2, ChronoUnit.DAYS)))
      )
    )
    h.latestDeployer.get shouldBe DeployerAudit("userb", TimeSeen(now.minus(1, ChronoUnit.DAYS)))
  }
}
