resolvers += Resolver.bintrayIvyRepo("hmrc", "sbt-plugin-releases")
resolvers += Resolver.bintrayRepo("hmrc", "releases")

addSbtPlugin("uk.gov.hmrc"       % "sbt-auto-build"     % "2.13.0")
addSbtPlugin("uk.gov.hmrc"       % "sbt-git-versioning" % "2.2.0")
addSbtPlugin("uk.gov.hmrc"       % "sbt-distributables" % "2.1.0")
addSbtPlugin("com.typesafe.play" % "sbt-plugin"         % "2.8.7")
