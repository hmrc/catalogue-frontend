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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.LocalDateTime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HeritageDeploymentSpec extends AnyFlatSpec with Matchers {

  "LastDeployer" should "select the most recent deployer" in {

    val now = LocalDateTime.now()
    val h = HeritageDeployment(
      ServiceName("foo"),
      VersionNumber("1.1.1"),
      Seq.empty,
      TimeSeen(LocalDateTime.now()),
      None,
      Seq(
        DeployerAudit("usera", TimeSeen(now.minusDays(3))),
        DeployerAudit("userb", TimeSeen(now.minusDays(1))),
        DeployerAudit("userc", TimeSeen(now.minusDays(2)))
      )
    )
    h.latestDeployer.get shouldBe DeployerAudit("userb", TimeSeen(now.minusDays(1)))
  }
}
