package uk.gov.hmrc.cataloguefrontend.healthindicators

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.http.HeaderCarrier

class HealthIndicatorsConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockEndpoints with OptionValues {

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.health-indicators.port" -> endpointPort,
          "microservice.services.health-indicators.host" -> host,
          "metrics.jvm"                                  -> false
        ))
      .build()

  private lazy val healthIndicatorsConnector = app.injector.instanceOf[HealthIndicatorsConnector]

  "getHealthIndicators()" should {
    "return a repository rating when given a valid repository name" in {
      serviceEndpoint(
        GET,
        "/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (200, Some(testJson))
      )

      val response = healthIndicatorsConnector.getHealthIndicators("team-indicator-dashboard-frontend").futureValue.value

      val ratings = Seq(
        Rating(
          "BobbyRule",
          -400,
          Seq(
            Score(-100, "frontend-bootstrap - Bug in Metrics Reporting", None),
            Score(-100, "frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)", None)
          )
        ),
        Rating("LeakDetection", 0, Seq()),
        Rating("ReadMe", -50, Seq(Score(-50, "No Readme defined", None)))
      )

      val expectedResponse = RepositoryRating("team-indicator-dashboard-frontend", -450, Some(ratings))

      response shouldBe expectedResponse
    }

    "return None when repository is not found" in {
      serviceEndpoint(
        GET,
        "/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          404,
          None
        ))

      val response = healthIndicatorsConnector.getHealthIndicators("team-indicator-dashboard-frontend").futureValue

      response shouldBe None
    }
  }

  private val testJson: String = """{
                   |  "repositoryName": "team-indicator-dashboard-frontend",
                   |  "repositoryScore": -450,
                   |  "ratings": [
                   |    {
                   |      "ratingType": "BobbyRule",
                   |      "ratingScore": -400,
                   |      "breakdown": [
                   |        {
                   |          "points": -100,
                   |          "description": "frontend-bootstrap - Bug in Metrics Reporting"
                   |        },
                   |        {
                   |          "points": -100,
                   |          "description": "frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)"
                   |        }
                   |      ]
                   |    },
                   |    {
                   |      "ratingType": "LeakDetection",
                   |      "ratingScore": 0,
                   |      "breakdown": []
                   |    },
                   |    {
                   |      "ratingType": "ReadMe",
                   |      "ratingScore": -50,
                   |      "breakdown": [
                   |        {
                   |          "points": -50,
                   |          "description": "No Readme defined"
                   |        }
                   |      ]
                   |    }
                   |  ]
                   |}""".stripMargin
}
