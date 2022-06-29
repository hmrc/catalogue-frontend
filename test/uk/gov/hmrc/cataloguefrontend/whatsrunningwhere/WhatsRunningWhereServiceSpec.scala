package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model.{ServiceDeploymentConfig, ServiceDeploymentConfigSummary}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class WhatsRunningWhereServiceSpec extends AnyWordSpec with Matchers {

  private val release1 = WhatsRunningWhere(ServiceName("address-lookup"),
    List(
      WhatsRunningWhereVersion(Environment.Development, VersionNumber("1.011")),
      WhatsRunningWhereVersion(Environment.Production, VersionNumber("1.011"))
    )
  )

  private val release2 = WhatsRunningWhere(ServiceName("health-indicators"),
    List(
      WhatsRunningWhereVersion(Environment.QA, VersionNumber("1.011")),
      WhatsRunningWhereVersion(Environment.Staging, VersionNumber("1.011")),
      WhatsRunningWhereVersion(Environment.Production, VersionNumber("1.011"))
    )
  )

  private val releases: Seq[WhatsRunningWhere] = Seq(release1, release2)
  private val configConnector: ConfigConnector = mock[ConfigConnector]
  private val releasesConnector: ReleasesConnector = mock[ReleasesConnector]

  val testService = new WhatsRunningWhereService(releasesConnector, configConnector)

  "whatsRunningWhereService.allReleases" should {
    "return the expected data structure and be filtered by releases" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      when(configConnector.allDeploymentConfig) thenReturn(
        Future.successful(Seq(
          ServiceDeploymentConfig(serviceName= "address-lookup", environment = "development", slots = 2, instances = 2),
          ServiceDeploymentConfig(serviceName= "address-lookup", environment = "qa", slots = 2, instances = 2),
          ServiceDeploymentConfig(serviceName= "address-lookup", environment = "production", slots = 4, instances = 4),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "development", slots = 3, instances = 3),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "qa", slots = 3, instances = 2),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "staging", slots = 5, instances = 5),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "production", slots = 8, instances = 4),
          ServiceDeploymentConfig(serviceName= "PODS", environment = "production", slots = 16, instances = 2),
          ServiceDeploymentConfig(serviceName= "file-upload", environment = "qa", slots = 2, instances = 1)
        ))
      )

      val expectedResult = Seq(
        ServiceDeploymentConfigSummary("address-lookup", Seq(
          ServiceDeploymentConfig(serviceName= "address-lookup", environment = "development", slots = 2, instances = 2),
          ServiceDeploymentConfig(serviceName= "address-lookup", environment = "production", slots = 4, instances = 4))),
        ServiceDeploymentConfigSummary("health-indicators", Seq(
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "qa", slots = 3, instances = 2),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "staging", slots = 5, instances = 5),
          ServiceDeploymentConfig(serviceName= "health-indicators", environment = "production", slots = 8, instances = 4)
        ))
      )

      Await.result(testService.allReleases(releases), 5.seconds) shouldBe expectedResult

    }
  }
}