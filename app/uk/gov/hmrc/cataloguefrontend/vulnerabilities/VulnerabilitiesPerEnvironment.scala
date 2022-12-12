package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}

case class VulnerabilitiesCount(
  actionRequired   : Int
, investigation    : Int
, noActionRequired : Int
)

object VulnerabilitiesCount {
  val reads: Reads[VulnerabilitiesCount] =
    ((__ \ "actionRequired").read[Int]
      ~ (__ \ "investigation").read[Int]
      ~ (__ \ "noActionRequired").read[Int]
      )(VulnerabilitiesCount.apply _)
}

case class VulnerabilitiesPerEnvironment(
  service     : String
, qa          : VulnerabilitiesCount
, staging     : VulnerabilitiesCount
, externalTest: VulnerabilitiesCount
, production  : VulnerabilitiesCount
)

object VulnerabilitiesPerEnvironment {

  private implicit val vcReads: Reads[VulnerabilitiesCount] = VulnerabilitiesCount.reads

  val reads: Reads[VulnerabilitiesPerEnvironment] =
    ((__ \ "service").read[String]
      ~ (__ \ "qa").read[VulnerabilitiesCount]
      ~ (__ \ "staging").read[VulnerabilitiesCount]
      ~ (__ \ "externalTest").read[VulnerabilitiesCount]
      ~ (__ \ "production").read[VulnerabilitiesCount]
      )(VulnerabilitiesPerEnvironment.apply _)
}