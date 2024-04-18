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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, DeploymentSize, Zone}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.LifecycleStatus.{Active, DecommissionInProgress, Deprecated}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Lifecycle, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ServiceConfigsServiceSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with ArgumentMatchersSugar
     with ScalaFutures
     with IntegrationPatience {
  import ServiceConfigsService._

  private def toNextDeploymentFalse(config: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]): Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]] =
    config.view.mapValues(_.view.mapValues(_.map(_ -> false)).toMap).toMap

  private def update(
    config: Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]],
    k     : KeyName,
    ce    : ConfigEnvironment
  )(update: Seq[(ConfigSourceValue, Boolean)] => Seq[(ConfigSourceValue, Boolean)]
  ): Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]] =
    config + (k -> (config.getOrElse(k, Map.empty) + (ce -> update(config.getOrElse(k, Map.empty).getOrElse(ce, Seq.empty)))))

  "serviceConfigsService.configByKey" should {
    val deployedConfigByKey = Map(
      KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
        ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e.asString}-a1"),
        ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e.asString}-b1")
      )).toMap)

    "return no change when latest and deployed are the same" in new Setup {
      val serviceName = "service"

      val config: Map[ConfigEnvironment, Seq[ConfigSourceEntries]] =
        ConfigEnvironment
          .values
          .map(e => e -> Seq(
            ConfigSourceEntries(source = "appConfigCommon"     , sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-a1"))
          , ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-b1"))
          )).toMap

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(config))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(config))

      serviceConfigsService.configByKeyWithNextDeployment(serviceName).futureValue shouldBe toNextDeploymentFalse(deployedConfigByKey)
    }

    "show undeployed changes" in new Setup {
      val serviceName = "service"

      val deployed: Map[ConfigEnvironment, Seq[ConfigSourceEntries]] =
        ConfigEnvironment
          .values
          .map(e => e -> Seq(
            ConfigSourceEntries(source = "appConfigCommon"     , sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-a1"))
          , ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-b1"))
          )).toMap

      val latest = deployed ++ Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> Seq(
        ConfigSourceEntries(source = "appConfigCommon",      sourceUrl = None, entries = Map(KeyName("k1") -> "qa-a1"))
      , ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> "new-val"))
      ))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      serviceConfigsService.configByKeyWithNextDeployment(serviceName).futureValue shouldBe update(
        toNextDeploymentFalse(deployedConfigByKey)
      , KeyName("k1")
      , ConfigEnvironment.ForEnvironment(Environment.QA)
      )(_ :+ (ConfigSourceValue("appConfigEnvironment", None, "new-val") -> true))
    }

    "show new undeployed keys" in new Setup {
      val serviceName = "service"

      val deployed: Map[ConfigEnvironment, Seq[ConfigSourceEntries]] =
        ConfigEnvironment
          .values
          .map(e => e -> Seq(
            ConfigSourceEntries(source = "appConfigCommon"     , sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-a1"))
          , ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-b1"))
          )).toMap

      val latest = deployed ++ Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> (
        deployed(ConfigEnvironment.ForEnvironment(Environment.QA)) :+
        ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k2") -> "new-val"))
      ))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      serviceConfigsService.configByKeyWithNextDeployment(serviceName).futureValue shouldBe update(
        toNextDeploymentFalse(deployedConfigByKey)
      , KeyName("k2")
      , ConfigEnvironment.ForEnvironment(Environment.QA)
      )(_ :+ (ConfigSourceValue("appConfigEnvironment", None, "new-val") -> true))
    }

    "show undeployed key removals" in new Setup {
      val serviceName = "service"

      val deployed: Map[ConfigEnvironment, Seq[ConfigSourceEntries]] =
        ConfigEnvironment
          .values
          .map(e => e -> Seq(
            ConfigSourceEntries(source = "appConfigCommon"     , sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-a1"))
          , ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> s"${e.asString}-b1"))
          )).toMap

      val latest = deployed ++ Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> (
        deployed(ConfigEnvironment.ForEnvironment(Environment.QA)) :+
        ConfigSourceEntries(source = "", sourceUrl = None, entries = Map(KeyName("k1") -> ""))
      ))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      serviceConfigsService.configByKeyWithNextDeployment(serviceName).futureValue shouldBe update(
        toNextDeploymentFalse(deployedConfigByKey)
      , KeyName("k1")
      , ConfigEnvironment.ForEnvironment(Environment.QA)
      )(_ :+ (ConfigSourceValue("", None, "") -> true))
    }
  }

  "serviceConfigsService.toKeyServiceEnvironmentMap" should {
    "group by key, service and environment" in new Setup {
      serviceConfigsService.toKeyServiceEnvironmentMap(
        List(
          AppliedConfig(
            ServiceName("test-service"),
            KeyName("test.key"),
            Map(
              Environment.Production -> ConfigSourceValue("some-source", Some("some-url"), "prodValue"),
              Environment.QA         -> ConfigSourceValue("some-source", Some("some-url"), "qaValue")
            )
          )
        )
      ) shouldBe (
        Map(KeyName("test.key") -> Map(
          ServiceName("test-service") -> Map(
            Environment.Production -> ConfigSourceValue("some-source", Some("some-url"), "prodValue"),
            Environment.QA         -> ConfigSourceValue("some-source", Some("some-url"), "qaValue")
          )
        ))
      )
    }
  }

  "serviceConfigsService.toServiceKeyEnvironmentMap" should {
    "group by service, key and environment" in new Setup {
      serviceConfigsService.toServiceKeyEnvironmentMap(
        List(
          AppliedConfig(
            ServiceName("test-service"),
            KeyName("test.key"),
            Map(
              Environment.Production -> ConfigSourceValue("some-source", Some("some-url"), "prodValue"),
              Environment.QA         -> ConfigSourceValue("some-source", Some("some-url"), "qaValue")
            )
          )
        )
      ) shouldBe (
        Map(ServiceName("test-service") -> Map(
          KeyName("test.key") -> Map(
            Environment.Production -> ConfigSourceValue("some-source", Some("some-url"), "prodValue"),
            Environment.QA         -> ConfigSourceValue("some-source", Some("some-url"), "qaValue")
          )
        ))
      )
    }

    "serviceConfigsService.deploymentConfigChanges" should {
      "show changed deployment sizes" in new Setup {
        val serviceName = ServiceName("test-service")
        val environment = Environment.Production
        when(mockServiceConfigsConnector.deploymentConfig(Some(serviceName.asString), Some(environment), applied = true))
          .thenReturn(Future.successful(Seq(
            DeploymentConfig(
              serviceName.asString,
              DeploymentSize(slots = 1, instances = 2),
              environment = environment,
              zone        = Zone.Protected,
              envVars     = Map("k1" -> "v1"),
              jvm         = Map("k2" -> "v2")
            )
          )))
        when(mockServiceConfigsConnector.deploymentConfig(Some(serviceName.asString), Some(environment), applied = false))
          .thenReturn(Future.successful(Seq(
            DeploymentConfig(
              serviceName.asString,
              DeploymentSize(slots = 3, instances = 4),
              environment = environment,
              zone        = Zone.Protected,
              envVars     = Map("k1" -> "v1"),
              jvm         = Map("k2" -> "v2")
            )
          )))
        serviceConfigsService.deploymentConfigChanges(serviceName, environment).futureValue shouldBe Seq(
          ConfigChange.ChangedConfig("instances", previousV = "2", newV = "4"),
          ConfigChange.ChangedConfig("slots"    , previousV = "1", newV = "3")
        )
      }

      "show changed envvars and jvm" in new Setup {
        val serviceName = ServiceName("test-service")
        val environment = Environment.Production
        when(mockServiceConfigsConnector.deploymentConfig(Some(serviceName.asString), Some(environment), applied = true))
          .thenReturn(Future.successful(Seq(
            DeploymentConfig(
              serviceName.asString,
              DeploymentSize(slots = 1, instances = 2),
              environment = environment,
              zone        = Zone.Protected,
              envVars     = Map(
                              "evk1" -> "evv1",
                              "evk2" -> "evv2b",
                              "evk3" -> "evv3"
                            ),
              jvm         = Map(
                              "jk1" -> "jv1",
                              "jk2" -> "jv2b",
                              "jk3" -> "jv3"
                            )
            )
          )))
        when(mockServiceConfigsConnector.deploymentConfig(Some(serviceName.asString), Some(environment), applied = false))
          .thenReturn(Future.successful(Seq(
            DeploymentConfig(
              serviceName.asString,
              DeploymentSize(slots = 1, instances = 2),
              environment = environment,
              zone        = Zone.Protected,
              envVars     = Map(
                              "evk1" -> "evv1",
                              "evk2" -> "evv2",
                              "evk4" -> "evv4"
                            ),
              jvm         = Map(
                              "jk1" -> "jv1",
                              "jk2" -> "jv2",
                              "jk4" -> "jv4"
                            )
            )
          )))
        serviceConfigsService.deploymentConfigChanges(serviceName, environment).futureValue shouldBe Seq(
          ConfigChange.ChangedConfig("environment.evk2", "evv2b", "evv2"),
          ConfigChange.DeletedConfig("environment.evk3", "evv3"),
          ConfigChange.NewConfig("environment.evk4", "evv4"),
          ConfigChange.ChangedConfig("jvm.jk2", "jv2b", "jv2"),
          ConfigChange.DeletedConfig("jvm.jk3", "jv3"),
          ConfigChange.NewConfig("jvm.jk4", "jv4")
        )
      }
    }
  }

  "serviceConfigsService.serviceRelationships" should {

    def gitRepository(name: String) =
      GitRepository(
        name = name,
        description = "test",
        githubUrl = "test",
        createdDate = Instant.now(),
        lastActiveDate = Instant.now(),
        language = Some("en"),
        isArchived = false,
        defaultBranch = "test",
        serviceType = None
      )

    "return list of service relationships with lifecycle status for outbound services when found in teams and repositories" in new Setup  {

      when(mockTeamsAndRepositoriesConnector.allRepositories())
        .thenReturn(Future.successful(Seq(gitRepository("test-repo-1"), gitRepository("inbound-repo-1"), gitRepository("outbound-repo-1"))))

      when(mockServiceConfigsConnector.serviceRelationships("test-repo-1"))
        .thenReturn(Future.successful(ServiceRelationships(Seq("inbound-repo-1" ), Seq("outbound-repo-1"))))

      when(mockServiceCommissioningConnector.getLifecycle("outbound-repo-1"))
        .thenReturn(Future.successful(Some(Lifecycle(DecommissionInProgress, None, None))))

      val expectedResult: ServiceRelationshipsEnriched = ServiceRelationshipsEnriched(
        Seq(ServiceRelationship("inbound-repo-1",  hasRepo = true, lifecycleStatus = None)),
        Seq(ServiceRelationship("outbound-repo-1", hasRepo = true, Some(DecommissionInProgress)))
      )

      serviceConfigsService.serviceRelationships("test-repo-1").futureValue shouldBe expectedResult
    }

    "return list of service relationships without lifecycle status when not found in teams and repositories" in new Setup {

      when(mockTeamsAndRepositoriesConnector.allRepositories())
        .thenReturn(Future.successful(Seq.empty))

      when(mockServiceConfigsConnector.serviceRelationships("test-repo-1"))
        .thenReturn(Future.successful(ServiceRelationships(Seq("inbound-repo-1"), Seq("outbound-repo-1"))))

      val expectedResult = ServiceRelationshipsEnriched(
        Seq(ServiceRelationship("inbound-repo-1",  hasRepo = false, lifecycleStatus = None)),
        Seq(ServiceRelationship("outbound-repo-1", hasRepo = false, lifecycleStatus = None))
      )

      serviceConfigsService.serviceRelationships("test-repo-1").futureValue shouldBe expectedResult

      verifyZeroInteractions(mockServiceCommissioningConnector)
    }
  }

  "serviceConfigsService.ServiceRelationshipsEnriched.hasDeprecatedDownstream" should {

    "return true if an outbound service lifecycle status is Deprecated" in {

      val serviceRelationshipsEnriched = ServiceRelationshipsEnriched(
        inboundServices = Seq.empty,
        outboundServices = Seq(
          ServiceRelationship("test-1", hasRepo = false, lifecycleStatus = Some(Deprecated)),
          ServiceRelationship("test-2", hasRepo = false, lifecycleStatus = Some(Active)),
          ServiceRelationship("test-3", hasRepo = false, lifecycleStatus = None))
      )

      serviceRelationshipsEnriched.hasDeprecatedDownstream shouldBe true
    }

    "return false if all service lifecycle status' are not Deprecated" in {

      val serviceRelationshipsEnriched = ServiceRelationshipsEnriched(
        inboundServices = Seq.empty,
        outboundServices = Seq(
          ServiceRelationship("test-1", hasRepo = false, lifecycleStatus = Some(Active)),
          ServiceRelationship("test-2", hasRepo = false, lifecycleStatus = None))
      )

      serviceRelationshipsEnriched.hasDeprecatedDownstream shouldBe false
    }
  }

  trait Setup {
    implicit val hc: HeaderCarrier        = mock[HeaderCarrier]
    val mockServiceConfigsConnector       = mock[ServiceConfigsConnector]
    val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockServiceCommissioningConnector = mock[ServiceCommissioningStatusConnector]

    val serviceConfigsService =
      new ServiceConfigsService(
        mockServiceConfigsConnector,
        mockTeamsAndRepositoriesConnector,
        mockServiceCommissioningConnector
      )
  }
}
