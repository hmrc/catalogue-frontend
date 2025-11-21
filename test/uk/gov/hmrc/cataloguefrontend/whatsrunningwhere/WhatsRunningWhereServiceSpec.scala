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
import uk.gov.hmrc.cataloguefrontend.cost.{DeploymentConfig, DeploymentSize, Zone}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
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

  val testService = WhatsRunningWhereService(releasesConnector, serviceConfigsConnector)

  "whatsRunningWhereService.allReleases" should {
    "return the expected data structure and be filtered by releases" in {
      given HeaderCarrier = HeaderCarrier()

      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(serviceName = ServiceName("address-lookup"   ), environment = Environment.Development, deploymentSize = DeploymentSize(slots = 2 , instances = 2), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("address-lookup"   ), environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 2 , instances = 2), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("address-lookup"   ), environment = Environment.Production , deploymentSize = DeploymentSize(slots = 4 , instances = 4), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("health-indicators"), environment = Environment.Development, deploymentSize = DeploymentSize(slots = 3 , instances = 3), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("health-indicators"), environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 3 , instances = 2), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("health-indicators"), environment = Environment.Staging    , deploymentSize = DeploymentSize(slots = 5 , instances = 5), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("health-indicators"), environment = Environment.Production , deploymentSize = DeploymentSize(slots = 8 , instances = 4), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("PODS"             ), environment = Environment.Production , deploymentSize = DeploymentSize(slots = 16, instances = 2), Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(serviceName = ServiceName("file-upload"      ), environment = Environment.QA         , deploymentSize = DeploymentSize(slots = 2 , instances = 1), Zone.Protected, envVars = Map.empty, jvm = Map.empty)
        ))
      )

      testService.allDeploymentConfigs(releases).futureValue shouldBe Seq(
        ServiceDeploymentConfigSummary(ServiceName("address-lookup"), Map(
          Environment.Development -> DeploymentSize(slots = 2, instances = 2),
          Environment.Production  -> DeploymentSize(slots = 4, instances = 4)
        )),
        ServiceDeploymentConfigSummary(ServiceName("health-indicators"), Map(
          Environment.QA         -> DeploymentSize(slots = 3, instances = 2),
          Environment.Staging    -> DeploymentSize(slots = 5, instances = 5),
          Environment.Production -> DeploymentSize(slots = 8, instances = 4)
        ))
      )
    }
  }

  "whatsRunningWhereService.releases" should {
    "enrich releases with deployment type from deployment config" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil),
            WhatsRunningWhereVersion(Environment.Production, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("deployment.type" -> "appmesh"),
            jvm = Map.empty
          ),
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Production,
            deploymentSize = DeploymentSize(slots = 4, instances = 2),
            zone = Zone.Protected,
            envVars = Map("deploymentType" -> "consul"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      result should have size 1
      result.head.serviceName shouldBe ServiceName("test-service")
      result.head.versions should have size 2

      val devVersion = result.head.versions.find(_.environment == Environment.Development).get
      // Appmesh is standard, so deploymentType should be None (no icon shown)
      devVersion.deploymentType shouldBe None

      val prodVersion = result.head.versions.find(_.environment == Environment.Production).get
      prodVersion.deploymentType shouldBe Some(DeploymentType.Consul)
    }

    "handle missing deployment type gracefully" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map.empty,
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      result.head.versions.head.deploymentType shouldBe None
    }

    "detect Appmesh deployment type from different flag names" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("deployment_type" -> "appmesh"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      // Appmesh is standard, so deploymentType should be None (no icon shown)
      result.head.versions.head.deploymentType shouldBe None
    }

    "detect Consul deployment type from consul_migration_stage stage 2" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("consul_migration_stage" -> "2"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      result.head.versions.head.deploymentType shouldBe Some(DeploymentType.Consul)
    }

    "detect Consul deployment type from consul_migration_stage stage 3" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("consul_migration_stage" -> "3"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      result.head.versions.head.deploymentType shouldBe Some(DeploymentType.Consul)
    }

    "do not show icon for consul_migration_stage stage 0" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("consul_migration_stage" -> "0"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      // Stage 0: AppMesh (standard, no icon)
      result.head.versions.head.deploymentType shouldBe None
    }

    "do not show icon for consul_migration_stage stage 1" in {
      given HeaderCarrier = HeaderCarrier()

      val releasesData = Seq(
        WhatsRunningWhere(
          ServiceName("test-service"),
          List(
            WhatsRunningWhereVersion(Environment.Development, Version("1.0.0"), Nil)
          )
        )
      )

      when(releasesConnector.releases(None, None, None)).thenReturn(Future.successful(releasesData))
      when(serviceConfigsConnector.deploymentConfig()).thenReturn(
        Future.successful(Seq(
          DeploymentConfig(
            serviceName = ServiceName("test-service"),
            environment = Environment.Development,
            deploymentSize = DeploymentSize(slots = 2, instances = 1),
            zone = Zone.Protected,
            envVars = Map("consul_migration_stage" -> "1"),
            jvm = Map.empty
          )
        ))
      )

      val result = testService.releases(None, None, None).futureValue

      // Stage 1: AppMesh (standard, no icon)
      result.head.versions.head.deploymentType shouldBe None
    }
  }
}
