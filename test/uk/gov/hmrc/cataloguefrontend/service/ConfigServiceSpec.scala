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

package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.cataloguefrontend.{CatalogueFrontendSwitches, FeatureSwitch}
import uk.gov.hmrc.cataloguefrontend.connector.{ConfigConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConfigServiceSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with ArgumentMatchersSugar
     with ScalaFutures {
  import ConfigService._

  "ConfigService.configByKey" should {
    "ignore deployed when switched off" in new Setup {
      FeatureSwitch.disable(CatalogueFrontendSwitches.showDeployedConfig)

      val serviceName = "service"

      val latest: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Map(
          KeyName("k1") -> Map(
            ConfigEnvironment.ForEnvironment(Environment.Production) -> Seq(
              ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = "a1"),
              ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = "b1")
            )
          )
        )))

      configService.configByKey(serviceName).futureValue shouldBe latest
    }

    "show keys added to latest when feature switched off" in new Setup {
      FeatureSwitch.disable(CatalogueFrontendSwitches.showDeployedConfig)

      val serviceName = "service"

      val deployed: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      val latest =
        update(deployed)(KeyName("k2"), ConfigEnvironment.ForEnvironment(Environment.QA))(
          _ :+ ConfigSourceValue("appConfigCommon", None, "val")
        )

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      configService.configByKey(serviceName).futureValue shouldBe update(
        deployed
      )(KeyName("k2"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("appConfigCommon", None, "val")
      )
    }

    "return no change when latest and deployed are the same" in new Setup {
      val serviceName = "service"

      val config: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(config))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(config))

      configService.configByKey(serviceName).futureValue shouldBe config
    }

    "ignore envs without data (appConfigEnvironment)" in new Setup {
      val serviceName = "service"

      val latest: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      val deployed =
        latest.map { case (k, m) => k -> m.map {
          case (e@ConfigEnvironment.ForEnvironment(Environment.QA), _ ) => e -> Seq.empty
          case (e                                                 , vs) => e -> vs
          }
        }

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))


      configService.configByKey(serviceName).futureValue shouldBe latest
    }

    "show undeployed changes" in new Setup {
      val serviceName = "service"

      val deployed: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      val latest =
        deployed.map { case (k, m) => k -> m.map {
          case (e@ConfigEnvironment.ForEnvironment(Environment.QA), _ ) => e -> Seq(ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"new-val"))
          case (e                                                 , vs) => e -> vs
        }}

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      configService.configByKey(serviceName).futureValue shouldBe update(
        deployed
      )(KeyName("k1"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "new-val")
      )
    }

    "show new undeployed keys" in new Setup {
      val serviceName = "service"

      val deployed: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      val latest =
        update(deployed)(KeyName("k2"), ConfigEnvironment.ForEnvironment(Environment.QA))(_ :+
          ConfigSourceValue("nextDeployment", None, "new-val")
        )

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      configService.configByKey(serviceName).futureValue shouldBe update(
        deployed
      )(KeyName("k2"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "new-val")
      )
    }

    "show undeployed key removals" in new Setup {
      val serviceName = "service"

      val deployed: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
        Map(
          KeyName("k1") -> ConfigEnvironment.values.map(e => e -> Seq(
            ConfigSourceValue(source = "appConfigCommon"     , sourceUrl = None, value = s"${e}-a1"),
            ConfigSourceValue(source = "appConfigEnvironment", sourceUrl = None, value = s"${e}-b1")
          )).toMap
        )

      val latest =
        update(deployed)(KeyName("k1"), ConfigEnvironment.ForEnvironment(Environment.QA))(_ => Seq.empty)

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(latest))

      when(mockConfigConnector.configByKey(eqTo(serviceName), latest = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.successful(deployed))

      configService.configByKey(serviceName).futureValue shouldBe update(
        deployed
      )(KeyName("k1"), ConfigEnvironment.ForEnvironment(Environment.QA))(
        _ :+ ConfigSourceValue("nextDeployment", None, "")
      )
    }
  }

  "ConfigService.searchAppliedConfig" should {
    "group by key, service and environment" in new Setup {
      when(mockConfigConnector.configSearch(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Seq(
            AppliedConfig(Environment.Production, ServiceName("test-service"), KeyName("test.key"), "prodValue"),
            AppliedConfig(Environment.QA, ServiceName("test-service"), KeyName("test.key"), "qaValue")
          )
        ))

      val expected: Map[KeyName, Map[ServiceName, Map[Environment, Option[String]]]] = Map(
        KeyName("test.key") -> Map(
          ServiceName("test-service") -> Map(
            Environment.Production -> Some("prodValue"),
            Environment.QA -> Some("qaValue")
          )
        )
      )

      configService
        .searchAppliedConfig("test.key")
        .futureValue shouldBe expected
    }
  }

  trait Setup {
    implicit val hc: HeaderCarrier = mock[HeaderCarrier]

    val mockConfigConnector = mock[ConfigConnector]
    val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]

    val configService = new ConfigService(mockConfigConnector, mockTeamsAndRepositoriesConnector)

    FeatureSwitch.enable(CatalogueFrontendSwitches.showDeployedConfig)

    def update(config: Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]])(k: KeyName, ce: ConfigEnvironment)(update: Seq[ConfigSourceValue] => Seq[ConfigSourceValue]): Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]] =
      config + (k -> (config.getOrElse(k, Map.empty) + (ce -> update(config.getOrElse(k, Map.empty).getOrElse(ce, Seq.empty)))))
  }
}
