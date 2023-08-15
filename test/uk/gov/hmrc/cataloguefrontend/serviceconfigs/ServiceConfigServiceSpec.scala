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
import org.scalatest.concurrent.{ScalaFutures, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier

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

      serviceConfigsService.configByKey(serviceName).futureValue shouldBe deployedConfigByKey
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

      serviceConfigsService.configByKey(serviceName).futureValue shouldBe update(
        deployedConfigByKey
      )(KeyName("k1"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "new-val")
      )
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

      serviceConfigsService.configByKey(serviceName).futureValue shouldBe update(
        deployedConfigByKey
      )(KeyName("k2"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "new-val")
      )
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
        ConfigSourceEntries(source = "appConfigEnvironment", sourceUrl = None, entries = Map(KeyName("k1") -> ""))
      ))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockServiceConfigsConnector.configByEnv(eqTo(serviceName), environments = eqTo(Nil), version = eqTo(None), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      serviceConfigsService.configByKey(serviceName).futureValue shouldBe update(
        deployedConfigByKey
      )(KeyName("k1"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "")
      )
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
  }

  trait Setup {
    implicit val hc: HeaderCarrier        = mock[HeaderCarrier]
    val mockServiceConfigsConnector       = mock[ServiceConfigsConnector]
    val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val serviceConfigsService             = new ServiceConfigsService(mockServiceConfigsConnector, mockTeamsAndRepositoriesConnector)

    def update(config: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]])
              (k: KeyName, ce: ConfigEnvironment)
              (update: Seq[ConfigSourceValue] => Seq[ConfigSourceValue]): Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
      config + (k -> (config.getOrElse(k, Map.empty) + (ce -> update(config.getOrElse(k, Map.empty).getOrElse(ce, Seq.empty)))))
  }
}
