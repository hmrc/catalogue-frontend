resolvers ++= Seq(
  Resolver.bintrayIvyRepo("hmrc", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases")
)

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.12.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.7.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "1.1.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.12.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.14")
