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

package uk.gov.hmrc.cataloguefrontend.createappconfigs

import cats.data.EitherT
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, GitRepository, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.Check.{EnvCheck, Present, SimpleCheck}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{CreateAppConfigsPage, error_404_template}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class CreateAppConfigsController @Inject()(
  override val auth                  : FrontendAuthComponents,
  override val mcc                   : MessagesControllerComponents,
  createAppConfigsPage               : CreateAppConfigsPage,
  buildDeployApiConnector            : BuildDeployApiConnector,
  teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector,
  serviceCommissioningStatusConnector: ServiceCommissioningStatusConnector,
  serviceDependenciesConnector       : ServiceDependenciesConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with I18nSupport {

  private val logger = Logger(getClass)


  private def checkAppConfigBaseExists(checks: List[Check]): Boolean =
    checks.exists {
      case SimpleCheck("App Config Base", Right(Present(_)), _, _) => true
      case _                                                       => false
    }

  private def checkAppConfigEnvExists(checks: List[Check]): Seq[Environment] =
    checks.flatMap {
      case EnvCheck("App Config Environment", checkResults, _, _) => checkResults.collect {
        case (env, Right(Present(_))) => env
      }
      case _                                                      => Seq.empty[Environment]
    }

  def createAppConfigsPermission(serviceName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-repository", serviceName), IAAction("CREATE_APP_CONFIGS"))

  def createAppConfigsLanding(serviceName: String): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateAppConfigsController.createAppConfigsLanding(serviceName),
      retrieval   = Retrieval.hasPredicate(createAppConfigsPermission(serviceName))
    ) .async { implicit request =>
      (
        for {
          repo         <- EitherT.fromOptionF[Future, Result, GitRepository](
                            teamsAndRepositoriesConnector.repositoryDetails(serviceName),
                            NotFound(error_404_template())
                          )
          serviceType  <- EitherT.fromOption[Future](repo.serviceType, {
                            logger.error(s"$serviceName is missing a Service Type")
                            NotFound(error_404_template())
                          })
          configChecks <- EitherT.liftF[Future, Result, List[Check]](
                            serviceCommissioningStatusConnector.commissioningStatus(serviceName)
                              .map(_.getOrElse(List.empty[Check]))
                          )
          baseConfig    = checkAppConfigBaseExists(configChecks)
          envsConfig    = checkAppConfigEnvExists(configChecks)
          hasPerm       = request.retrieval
          form          = CreateAppConfigsForm.form.bindFromRequest()
          form2         = if (!hasPerm) form.withGlobalError(s"You do not have permission to create App Configs for: $serviceName") else form
        } yield Ok(createAppConfigsPage(form2, serviceName, serviceType, hasPerm, baseConfig, envsConfig))
      ).merge
    }


  def createAppConfigs(serviceName: String): Action[AnyContent] =
    auth.authorizedAction(
      predicate   = createAppConfigsPermission(serviceName),
      continueUrl = routes.CreateAppConfigsController.createAppConfigsLanding(serviceName)
    ) .async { implicit request =>
      (
        for {
          repo          <- EitherT.fromOptionF[Future, Result, GitRepository](
                             teamsAndRepositoriesConnector.repositoryDetails(serviceName),
                             NotFound(error_404_template())
                           )
          serviceType   <- EitherT.fromOption[Future](repo.serviceType, InternalServerError("No Service Type"))
          form          <- EitherT.fromEither[Future](CreateAppConfigsForm.form.bindFromRequest().fold(
                             formWithErrors =>
                               Left(
                                 BadRequest(
                                   createAppConfigsPage(
                                     form          = formWithErrors,
                                     serviceName   = serviceName,
                                     serviceType   = serviceType,
                                     hasPerm       = true,
                                     hasBaseConfig = false,
                                     envConfigs    = Seq.empty
                                   )
                                 )
                               ),
                             validForm => Right(validForm)
                           ))
          _             <- EitherT.liftF(auth.authorised(Some(createAppConfigsPermission(serviceName))))
          requiresMongo <- EitherT.liftF[Future, Result, Boolean](
                             serviceDependenciesConnector.getSlugInfo(serviceName)
                               .map(_.exists(_.dependencyDotCompile.exists(_.contains("\"hmrc-mongo\""))))
                           )
          id            <- EitherT(buildDeployApiConnector.createAppConfigs(form, serviceName, serviceType, requiresMongo)).leftMap { errMsg =>
                             logger.info(s"createAppConfigs failed with: $errMsg")
                             InternalServerError(
                               createAppConfigsPage(
                                 form          = CreateAppConfigsForm.form.bindFromRequest().withGlobalError(errMsg),
                                 serviceName   = serviceName,
                                 serviceType   = serviceType,
                                 hasPerm       = true,
                                 hasBaseConfig = false,
                                 envConfigs    = Seq.empty
                               )
                             )
                           }
          _              = logger.info(s"Bnd api request id: $id:")
        } yield
          Redirect(uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes.ServiceCommissioningStatusController.getCommissioningState(serviceName))
     ).merge
    }
}

case class CreateAppConfigsForm(
  appConfigBase       : Boolean,
  appConfigDevelopment: Boolean,
  appConfigQA         : Boolean,
  appConfigStaging    : Boolean,
  appConfigProduction : Boolean
)

object CreateAppConfigsForm {
  val form: Form[CreateAppConfigsForm] = Form(
    mapping(
      "appConfigBase"        -> boolean,
      "appConfigDevelopment" -> boolean,
      "appConfigQA"          -> boolean,
      "appConfigStaging"     -> boolean,
      "appConfigProduction"  -> boolean,
    )(CreateAppConfigsForm.apply)(CreateAppConfigsForm.unapply)
  )
}
