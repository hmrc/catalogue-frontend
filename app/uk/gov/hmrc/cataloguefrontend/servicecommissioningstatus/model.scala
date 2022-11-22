package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.Environment


case class StatusCheck(
  evidence: Option[String]
, status  : Boolean
)

object StatusCheck {
  val reads: Reads[StatusCheck] =
    ( (__ \ "evidence"  ).readNullable[String]
      ~ (__ \ "status"  ).read[Boolean]
      ) (StatusCheck.apply _)
}


case class FrontendRoutes(asMap: Map[Environment, StatusCheck])

case class DeploymentEnvironment(asMap: Map[Environment, StatusCheck])

case class AppConfigEnvironment(asMap: Map[Environment, StatusCheck])

case class Dashboard(
  kibana  : StatusCheck
, grafana : StatusCheck
)

object Dashboard {
  val reads: Reads[Dashboard] = {
    implicit val scReads: Reads[StatusCheck] = StatusCheck.reads
    ((__ \ "kibana").read[StatusCheck]
      ~ (__ \ "grafana").read[StatusCheck]
      ) (Dashboard.apply _)
  }
}

case class ServiceCommissioningStatus(
  serviceName      : String
, hasRepo          : StatusCheck
, hasSMConfig      : StatusCheck
, hasFrontendRoutes: FrontendRoutes
, hasAppConfigBase : StatusCheck
, hasAppConfigEnv  : AppConfigEnvironment
, isDeployed       : DeploymentEnvironment
, hasDashboards    : Dashboard
, hasBuildJobs     : StatusCheck
, hasAlerts        : StatusCheck
)

object ServiceCommissioningStatus {
  private implicit val scReads: Reads[StatusCheck] = StatusCheck.reads
  private implicit val dsReads: Reads[Dashboard] = Dashboard.reads

  private val mapFormat: Reads[Map[Environment, StatusCheck]] =
    Reads
      .of[Map[String, StatusCheck]]
      .map(
        _.map { case (k, v) => (Environment.parse(k).getOrElse(sys.error("Invalid Environment")), v) }
      )

  private implicit val frReads: Reads[FrontendRoutes] =
    mapFormat.map(FrontendRoutes.apply)

  private implicit val deReads: Reads[DeploymentEnvironment] =
    mapFormat.map(DeploymentEnvironment.apply)

  private implicit val acReads: Reads[AppConfigEnvironment] =
    mapFormat.map(AppConfigEnvironment.apply)


  val writes: Reads[ServiceCommissioningStatus] =
    ( (__ \ "serviceName"        ).read[String]
      ~ (__ \ "hasRepo"          ).read[StatusCheck]
      ~ (__ \ "hasSMConfig"      ).read[StatusCheck]
      ~ (__ \ "hasFrontendRoutes").read[FrontendRoutes]
      ~ (__ \ "hasAppConfigBase" ).read[StatusCheck]
      ~ (__ \ "hasAppConfigEnv"  ).read[AppConfigEnvironment]
      ~ (__ \ "isDeployedIn"     ).read[DeploymentEnvironment]
      ~ (__ \ "hasDashboards"    ).read[Dashboard]
      ~ (__ \ "hasBuildJobs"     ).read[StatusCheck]
      ~ (__ \ "hasAlerts"        ).read[StatusCheck]
      ) (ServiceCommissioningStatus.apply _)
}