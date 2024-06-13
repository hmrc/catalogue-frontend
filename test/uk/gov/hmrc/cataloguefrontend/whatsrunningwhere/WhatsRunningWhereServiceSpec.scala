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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, DeploymentSize, Zone}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatsRunningWhereServiceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures {

  private val release1 =
    WhatsRunningWhere(
      ServiceName("address-lookup"),
      List(
        WhatsRunningWhereVersion(Environment.Development, Version("1.011"), Nil),
        WhatsRunningWhereVersion(Environment.Production,  Version("1.011"), Nil)
      )
    )

  private val release2 =
    WhatsRunningWhere(
      ServiceName("health-indicators"),
      List(
        WhatsRunningWhereVersion(Environment.QA,         Version("1.011"), Nil),
        WhatsRunningWhereVersion(Environment.Staging,    Version("1.011"), Nil),
        WhatsRunningWhereVersion(Environment.Production, Version("1.011"), Nil)
      )
    )

  private val releases: Seq[WhatsRunningWhere] = Seq(release1, release2)

  private val serviceConfigsConnector: ServiceConfigsConnector = mock[ServiceConfigsConnector]
  private val releasesConnector      : ReleasesConnector       = mock[ReleasesConnector]

  val testService = new WhatsRunningWhereService(releasesConnector, serviceConfigsConnector)

  "whatsRunningWhereService.allReleases" should {
    "return the expected data structure and be filtered by releases" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(serviceName= "address-lookup"   , environment = Environment.Development, deploymentSize = DeploymentSize(slots = 2 , instances = 2), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "address-lookup"   , environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 2 , instances = 2), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "address-lookup"   , environment = Environment.Production , deploymentSize = DeploymentSize(slots = 4 , instances = 4), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "health-indicators", environment = Environment.Development, deploymentSize = DeploymentSize(slots = 3 , instances = 3), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "health-indicators", environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 3 , instances = 2), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "health-indicators", environment = Environment.Staging    , deploymentSize = DeploymentSize(slots = 5 , instances = 5), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "health-indicators", environment = Environment.Production , deploymentSize = DeploymentSize(slots = 8 , instances = 4), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "PODS"             , environment = Environment.Production , deploymentSize = DeploymentSize(slots = 16, instances = 2), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName= "file-upload"      , environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 2 , instances = 1), zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty)
        ))
      )

      testService.allDeploymentConfigs(releases).futureValue shouldBe Seq(
        ServiceDeploymentConfigSummary("address-lookup", Map(
          Environment.Development -> DeploymentSize(slots = 2, instances = 2),
          Environment.Production  -> DeploymentSize(slots = 4, instances = 4)
        )),
        ServiceDeploymentConfigSummary("health-indicators", Map(
          Environment.QA         -> DeploymentSize(slots = 3, instances = 2),
          Environment.Staging    -> DeploymentSize(slots = 5, instances = 5),
          Environment.Production -> DeploymentSize(slots = 8, instances = 4)
        ))
      )
    }
  }
}
