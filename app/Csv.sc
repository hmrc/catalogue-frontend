
import com.github.tototoshi.csv._
import org.apache.commons.io.output.ByteArrayOutputStream
import uk.gov.hmrc.cataloguefrontend.connector.model.Version

val os = new ByteArrayOutputStream()

val writer = CSVWriter.open(new java.io.OutputStreamWriter(os))

writer.writeAll(Seq(Seq("name", "surname", "country"), Seq("armin", "keyvanloo", "UK")))

os.toString

os.close()

//cols: digital-service, team, repository, type(lib, plugin), current-version, latest-version, colour
//vals: xyz,abc, rrr, lib, 1.2.3, 1.2.4, orange

case class DependencyReport(repository: String,
                            team: String,
                            digitalService: Option[String],
                            dependencyType: String,
                            currentVersion: Version,
                            latestVersion: Version,
                            colour: String)

val dr = DependencyReport("some-repository",
  "some-team",
  Some("some-digitalService"),
  "dependencyType",
  Version(1,2,3),
  Version(2,3,4),
  "colour"
)

List(1,2,3).zip(Seq("a", "b", "c"))

import scala.reflect.runtime.universe._
typeOf[DependencyReport].members
typeOf[DependencyReport].members.collect {
  case m: MethodSymbol if m.isCaseAccessor => m
}.toList.reverse
