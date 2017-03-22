/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDateTime

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}


class ReleaseSpec  extends WordSpec with Matchers with MockitoSugar with ScalaFutures {

  "Release.latestDeployer" should {
    "give most recent deployer" in  {
      val now: LocalDateTime = LocalDateTime.now()
      Release("r", now.minusDays(30),None,None,None,"1.1",
        Seq(
          Deployer("d",now.minusDays(5)),
          Deployer("a",now.minusDays(10)),
          Deployer("b",now.minusDays(30)),
          Deployer("c",now.minusDays(20))
        )
      ).latestDeployer.get shouldBe Deployer("d",now.minusDays(5))

    }
  }

}
