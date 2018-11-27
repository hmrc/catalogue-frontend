package view.partials

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, Version}

class DependencySectionSpec extends WordSpec with Matchers {

  val dependency1 = Dependency("example-library", Version(1,2,3, Some("play-25")), Some(Version(1,2,3, Some("play-26"))))
  val dependency2 = Dependency("library4j", Version(4,0,1), Some(Version(4,2,0)))

  "dependency_section" should {

    "display the version suffix when present" in {
      val res = views.html.partials.dependency_section(Seq(dependency1)).body
      res should include ("<span id=\"example-library-current-version\" class=\"col-xs-3\">1.2.3-play-25</span>")
      res should include ("<span id=\"example-library-latestVersion-version\" class=\"col-xs-3\">1.2.3-play-26</span>")
    }

    "not display the version suffix when missing" in {
      val res = views.html.partials.dependency_section(Seq(dependency2)).body
      res should include ("<span id=\"library4j-current-version\" class=\"col-xs-3\">4.0.1</span>")
      res should include ("<span id=\"library4j-latestVersion-version\" class=\"col-xs-3\">4.2.0</span>")
    }
  }

}
