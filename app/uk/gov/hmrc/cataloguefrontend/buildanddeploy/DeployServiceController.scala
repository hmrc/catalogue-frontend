/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend.buildanddeploy

import cats.data.EitherT

import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, TeamsAndRepositoriesConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.service.SlugVersionInfo
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhereVersion}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesConnector

import uk.gov.hmrc.cataloguefrontend.model.Environment
// import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.Check.{EnvCheck, Present, SimpleCheck}
// import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.buildanddeploy.{DeployServicePage, DeployServiceConsolePage}

import javax.inject.{Inject, Singleton}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class DeployServiceController @Inject()(
  override val auth            : FrontendAuthComponents
, override val mcc             : MessagesControllerComponents
, configuration                : Configuration
, buildDeployApiConnector      : BuildDeployApiConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, serviceDependenciesConnector : ServiceDependenciesConnector
, serviceCommissioningConnector: ServiceCommissioningStatusConnector
, releasesConnector            : ReleasesConnector
, vulnerabilitiesConnector     : VulnerabilitiesConnector
, serviceConfigsService        : ServiceConfigsService
, deployServicePage            : DeployServicePage
, deployServiceConsolePage     : DeployServiceConsolePage
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with I18nSupport {

  private val logger = Logger(getClass)

  private val predicate: Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"services/platops-example-frontend-microservice"), IAAction("CREATE_APP_CONFIGS"))
    // Predicate.Permission(Resource.from("catalogue-frontend", "*"), IAAction("DEPLOY_SERVICE"))

  def step1(serviceName: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(serviceName),
      retrieval   = Retrieval.hasPredicate(predicate)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT.right[Result](teamsAndRepositoriesConnector.allServices())
        environments <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[Environment]))(sn =>
                          EitherT
                            .right[Result](serviceCommissioningConnector.commissioningStatus(sn))
                            .map(_.getOrElse(Nil))
                            .map(_.collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }.flatten )
                            .map(_.sorted)
                        )
        releases     <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[WhatsRunningWhereVersion]))(sn =>
                          EitherT
                            .right[Result](releasesConnector.releasesForService(sn))
                            .map(_.versions.toSeq)
                        )
        latest       <- serviceName.fold(EitherT.rightT[Future, Result](Option.empty[Version]))(sn =>
                          EitherT
                            .right[Result](serviceDependenciesConnector.getSlugInfo(sn))
                            .map(_.map(_.version))
                        )
        hasPerm      =  request.retrieval
        form         =  { val f = serviceName.fold(DeployServiceForm.form)(x => DeployServiceForm.form.bind(Map("serviceName" -> x)).discardingErrors)
                          if (!hasPerm) f.withGlobalError(s"You do not have permission to deploy")
                          else          f
                        }
      } yield
        Ok(deployServicePage(form, allServices, environments, releases, latest, evaluations = None))
      ).merge
    }

  def step2(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None),
      retrieval   = Retrieval.hasPredicate(predicate)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT
                          .right[Result](teamsAndRepositoriesConnector.allServices())
        form         =  DeployServiceForm.form.bindFromRequest()
        formObject   <- EitherT
                          .fromEither[Future](form.fold(
                            formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, allServices, Nil, Nil, None, evaluations = None)))
                          , validForm      => Right(validForm)
                          ))
        environments <- EitherT
                          .right[Result](serviceCommissioningConnector.commissioningStatus(formObject.serviceName))
                          .map(_.getOrElse(Nil))
                          .map(_.collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }.flatten )
                          .map(_.sorted)
        releases     <- EitherT
                          .right[Result](releasesConnector.releasesForService(formObject.serviceName))
                          .map(_.versions.toSeq)
        latest       <- EitherT
                          .right[Result](serviceDependenciesConnector.getSlugInfo(formObject.serviceName))
                          .map(_.map(_.version))
        slugInfo     <- EitherT
                          .fromOptionF(
                            serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                          , BadRequest(deployServicePage(form.withGlobalError("Slug not found"), allServices, environments, releases, latest, evaluations = None))
                          )
        warnings     <- EitherT
                          .right[Result](serviceConfigsService.configWarnings(ServiceConfigsService.ServiceName(formObject.serviceName), Seq(formObject.environment), version = Some(formObject.version), latest = true))
        vulnerabils  <- EitherT
                          .right[Result](vulnerabilitiesConnector.vulnerabilitySummaries(service = Some(formObject.serviceName), version = Some(formObject.version)))
      } yield
        Ok(deployServicePage(form, allServices, environments, releases, latest, Some((warnings, vulnerabils))))
      ).merge
    }

  def step3(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None),
      retrieval   = Retrieval.hasPredicate(predicate)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT.right[Result](teamsAndRepositoriesConnector.allServices())
        form         =  DeployServiceForm.form.bindFromRequest()
        formObject   <- EitherT
                          .fromEither[Future](form.fold(
                            formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, allServices, Nil, Nil, None, evaluations = None)))
                          , validForm      => Right(validForm)
                          ))
        slugInfo     <- EitherT
                          .fromOptionF(
                            serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                          , BadRequest(deployServicePage(form.withGlobalError("Slug not found"), allServices, Nil, Nil, None, evaluations = None))
                          )
        username     <- EitherT
                          .fromOption[Future](
                            request.session.get(AuthController.SESSION_USERNAME)
                          , sys.error("Username not found"): Result
                          )
        ecsTask      <- EitherT(buildDeployApiConnector.triggerMicroserviceDeployment(
                          serviceName = formObject.serviceName
                        , environment = formObject.environment
                        , version     = formObject.version
                        , slugSource  = slugInfo.uri
                        , deployerId  = username
                        )).leftMap { errMsg =>
                          sys.error(s"triggerMicroserviceDeployment failed with: $errMsg"): Result
                        }
        // ecsTask = BuildDeployApiConnector.RequestState.EcsTask("1", "2", "3", "4", "5")
      } yield
        Ok(deployServiceConsolePage(formObject, ecsTask))
      ).merge
    }

  import scala.concurrent.duration._
  import akka.stream.scaladsl.Source
  import play.api.http.ContentTypes
  import play.api.libs.EventSource
  import play.api.libs.json.Json
  def console(accountId: String, logGroupName: String, clusterName: String, logStreamNamePrefix: String, arn: String, start: Long): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None),
      retrieval   = Retrieval.hasPredicate(predicate)
    ) { implicit request =>
      val flow =
        Source
          .tick(0.millis, 10.second, "TICK")
          .mapAsync(parallelism = 1) { _ =>
            buildDeployApiConnector
              .getRequestState(BuildDeployApiConnector.RequestState.EcsTask(accountId, logGroupName, clusterName, logStreamNamePrefix, arn, start))
              .map {
                case Left(error)    => Json.toJson(error)
                // case Right(state) => Json.toJson(state)(Console.writes)
                case Right(jsValue) => jsValue
              }
          }
          .via(EventSource.flow)

      Ok.chunked(flow)
        .as(ContentTypes.EVENT_STREAM)
    }

}

case class DeployServiceForm(
  serviceName: String
, environment: Environment
, version    : Version
)

import play.api.data.{Form, Forms}
object DeployServiceForm {
  val form: Form[DeployServiceForm] = Form(
    Forms.mapping(
      "serviceName" -> Forms.text
    , "environment" -> Forms.of[Environment](Environment.formFormat)
    , "version"     -> Forms.of[Version](Version.formFormat)        //Forms.text.transform[Version](Version.apply, _.original)  // TODO Version parse isn't safe
    )(DeployServiceForm.apply)(DeployServiceForm.unapply)
  )
}

// object Console {
//   import play.api.libs.functional.syntax._
//   import play.api.libs.json._

//   val writes: Writes[BuildDeployApiConnector.RequestState] = {
//     implicit val writeLog: Writes[BuildDeployApiConnector.Log] =
//       ( (__ \ "bnd_api_request_attempt_number").write[Int]
//       ~ (__ \ "event"                         ).write[String]
//       ~ (__ \ "level"                         ).write[String]
//       ~ (__ \ "timestamp"                     ).write[Instant]
//       )(unlift(BuildDeployApiConnector.Log.unapply))

//     ( (__ \ "attempt_number"                 ).write[Int]
//     ~ (__ \ "logs"                           ).write[Seq[BuildDeployApiConnector.Log]]
//     ~ (__ \ "last_log_timestamp_milliseconds").write[Instant]
//     )(unlift(BuildDeployApiConnector.RequestState.unapply))
//   }
// }
