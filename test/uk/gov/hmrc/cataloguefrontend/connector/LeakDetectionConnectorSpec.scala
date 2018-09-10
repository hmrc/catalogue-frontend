package uk.gov.hmrc.cataloguefrontend.connector

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import scala.concurrent.Future

class LeakDetectionConnectorSpec extends WordSpec with Matchers with ScalaFutures with MockitoSugar {

  "repositoriesWithLeaks" should {
    "return empty if leak detection service returns status different than 2xx" in {
      implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
      val servicesConfig                        = mock[ServicesConfig]
      val httpClient                            = mock[HttpClient]

      when(servicesConfig.baseUrl(any())).thenReturn("http://leak-detection:8855")

      when(httpClient.GET(any())(any(), any(), any()))
        .thenReturn(Future.failed(new BadGatewayException("an exception")))

      val leakDetectionConnector = new LeakDetectionConnector(httpClient, servicesConfig)

      leakDetectionConnector.repositoriesWithLeaks.futureValue shouldBe Seq.empty
    }
  }
}
