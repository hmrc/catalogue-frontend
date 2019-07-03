package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.LocalDateTime

import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ShutterServiceSpec extends FlatSpec with MockitoSugar with Matchers {

  val mockEvents = Seq(
    ShutterEvent(
       name = "abc-frontend"
      ,env  = "production"
      ,user = "test.user"
      ,isShuttered = true
      ,date = LocalDateTime.now().minusDays(2)
    )
    ,ShutterEvent(
       name = "zxy-frontend"
      ,env  = "production"
      ,user = "fake.user"
      ,isShuttered = false
      ,date = LocalDateTime.now()
    )
    ,ShutterEvent(
      name = "ijk-frontend"
      ,env  = "production"
      ,user = "test.user"
      ,isShuttered = true
      ,date = LocalDateTime.now().minusDays(1)
    )
  )

  "findCurrentState" should "return a list of shutter events ordered by shutter status" in {

    val mockShutterConnector = mock[ShutterConnector]
    implicit val hc = new HeaderCarrier()

    when(mockShutterConnector.latestShutterEvents()).thenReturn(Future(mockEvents))
    val ss = new ShutterService(mockShutterConnector)

    val Seq(a,b,c) = Await.result(ss.findCurrentState(), Duration(10, "seconds"))

    a.isShuttered shouldBe true
    b.isShuttered shouldBe true
    c.isShuttered shouldBe false


  }

}
