package uk.gov.hmrc.cataloguefrontend.util

import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class MarkdownLoaderSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ScalaFutures {

  "MarkdownLoader.markdownFromString" should {
    "correctly apply markdown rules to a string" in {
      val s: String = "Test string [link this](https://thing.thing)"
      val result = MarkdownLoader.markdownFromString(s)
      result shouldBe Right("<p>Test string <a href=\"https://thing.thing\">link this</a></p>")
    }

    "return a string when markdown is formatted incorrectly" in {
      val s: String = "Test string (link this)[https://thing.thing]"
      val result = MarkdownLoader.markdownFromString(s)
      result.isLeft shouldBe true
    }
  }
}
