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
import play.api.data.{Form, Forms}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.createappconfigs.view.html.CreateAppConfigsPage
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, given}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.util.Parser
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

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
  serviceDependenciesConnector       : ServiceDependenciesConnector,
  gitHubProxyConnector               : GitHubProxyConnector,
  configuration                      : Configuration
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with I18nSupport:

  private val logger = Logger(getClass)

  import scala.jdk.CollectionConverters._

  private val envsToHide: Set[Environment] =
    configuration.underlying.getStringList("environmentsToHideByDefault").asScala.toSet
      .map: str =>
        Parser[Environment].parse(str).getOrElse(sys.error(s"config 'environmentsToHideByDefault' contains an invalid environment: $str"))

  private def checkAppConfigBaseExists(checks: List[Check]): Boolean =
    checks.exists:
      case Check.SimpleCheck("App Config Base", Right(Check.Present(_)), _, _) => true
      case _                                                                   => false

  private def checkAppConfigEnvExists(checks: List[Check]): Seq[Environment] =
    checks.flatMap:
      case Check.EnvCheck("App Config Environment", checkResults, _, _) =>
        checkResults.collect:
          case (env, Right(Check.Present(_))) => env
      case _                                                      =>
        Seq.empty[Environment]

  def createAppConfigsPermission(serviceName: ServiceName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"services/${serviceName.asString}"), IAAction("CREATE_APP_CONFIGS"))

  def createAppConfigsLanding(serviceName: ServiceName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateAppConfigsController.createAppConfigsLanding(serviceName),
      retrieval   = Retrieval.hasPredicate(createAppConfigsPermission(serviceName))
    ) .async { implicit request =>
      (
        for
          repo          <- EitherT.fromOptionF[Future, Result, GitRepository](
                             teamsAndRepositoriesConnector.repositoryDetails(serviceName.asString),
                             NotFound(error_404_template())
                           )
          serviceType   <- EitherT.fromOption[Future](repo.serviceType, {
                             logger.error(s"${serviceName.asString} is missing a Service Type")
                             NotFound(error_404_template())
                           })
          configChecks  <- EitherT.liftF[Future, Result, List[Check]](
                             serviceCommissioningStatusConnector.commissioningStatus(serviceName)
                           )
          baseConfig    =  checkAppConfigBaseExists(configChecks)
          envConfigs    =  checkAppConfigEnvExists(configChecks)
          isApi         =  serviceType == ServiceType.Backend && repo.tags.getOrElse(Set.empty[Tag]).contains(Tag.Api)
          envsToDisplay =  Environment.values.toSeq.diff(envsToHide.toSeq)
          hasPerm       =  request.retrieval
          form          =  { val f = CreateAppConfigsForm.form
                             if !hasPerm
                             then
                               f.withGlobalError(s"You do not have permission to create App Configs for: ${serviceName.asString}")
                             else
                               f.fill(CreateAppConfigsForm(true, true, true, true, true))
                           }
        yield
          Ok(
            createAppConfigsPage(
              form          = form,
              serviceName   = serviceName,
              serviceType   = serviceType,
              isApi         = isApi,
              hasPerm       = hasPerm,
              hasBaseConfig = baseConfig,
              envConfigs    = envConfigs,
              envsToDisplay = envsToDisplay
            )
          )
      ).merge
    }

  def createAppConfigs(serviceName: ServiceName): Action[AnyContent] =
    auth.authorizedAction(
      predicate   = createAppConfigsPermission(serviceName),
      continueUrl = routes.CreateAppConfigsController.createAppConfigsLanding(serviceName)
    ).async { implicit request =>
      (
        for
          repo          <- EitherT.fromOptionF[Future, Result, GitRepository](
                             teamsAndRepositoriesConnector.repositoryDetails(serviceName.asString),
                             NotFound(error_404_template())
                           )
          serviceType   <- EitherT.fromOption[Future](repo.serviceType, InternalServerError("No Service Type"))
          configChecks  <- EitherT.liftF[Future, Result, List[Check]](
                             serviceCommissioningStatusConnector.commissioningStatus(serviceName)
                           )
          baseConfig    =  checkAppConfigBaseExists(configChecks)
          envConfigs    =  checkAppConfigEnvExists(configChecks)
          isApi         =  serviceType == ServiceType.Backend && repo.tags.getOrElse(Set.empty[Tag]).contains(Tag.Api)
          envsToDisplay =  Environment.values.toSeq.diff(envsToHide.toSeq)
          form          <- EitherT.fromEither[Future](CreateAppConfigsForm.form.bindFromRequest().fold(
                             formWithErrors =>
                               Left(
                                 BadRequest(
                                   createAppConfigsPage(
                                     form          = formWithErrors,
                                     serviceName   = serviceName,
                                     serviceType   = serviceType,
                                     isApi         = isApi,
                                     hasPerm       = true,
                                     hasBaseConfig = baseConfig,
                                     envConfigs    = envConfigs,
                                     envsToDisplay = envsToDisplay
                                   )
                                 )
                               ),
                             validForm => Right(validForm)
                           ))
          _             <- EitherT.liftF(auth.authorised(Some(createAppConfigsPermission(serviceName))))
          optSlugInfo   <- EitherT.liftF(serviceDependenciesConnector.getSlugInfo(serviceName))
          requiresMongo <- optSlugInfo match
                             case Some(slugInfo) => EitherT.rightT[Future, Result](slugInfo.dependencyDotCompile.exists(_.contains("mongo")))
                             case None           => EitherT.liftF[Future, Result, Boolean]:
                                                      gitHubProxyConnector.getGitHubProxyRaw(s"/$serviceName/main/project/AppDependencies.scala")
                                                        .map(_.exists(_.contains("mongo")))
          id            <- EitherT(buildDeployApiConnector.createAppConfigs(form, serviceName, serviceType, requiresMongo, isApi))
                             .leftMap: errMsg =>
                               logger.info(s"createAppConfigs failed with: $errMsg")
                               InternalServerError(
                                 createAppConfigsPage(
                                   form          = CreateAppConfigsForm.form.bindFromRequest().withGlobalError(errMsg),
                                   serviceName   = serviceName,
                                   serviceType   = serviceType,
                                   isApi         = isApi,
                                   hasPerm       = true,
                                   hasBaseConfig = baseConfig,
                                   envConfigs    = envConfigs,
                                   envsToDisplay = envsToDisplay
                                 )
                               )
          _              = logger.info(s"Bnd api request id: $id:")
        yield
          Redirect(uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes.ServiceCommissioningStatusController.getCommissioningState(serviceName))
     ).merge
    }

end CreateAppConfigsController

case class CreateAppConfigsForm(
  appConfigBase        : Boolean,
  appConfigDevelopment : Boolean,
  appConfigQA          : Boolean,
  appConfigStaging     : Boolean,
  appConfigProduction  : Boolean
)

object CreateAppConfigsForm:
  val form: Form[CreateAppConfigsForm] =
    Form(
      Forms.mapping(
        "appConfigBase"         -> Forms.boolean,
        "appConfigDevelopment"  -> Forms.boolean,
        "appConfigQA"           -> Forms.boolean,
        "appConfigStaging"      -> Forms.boolean,
        "appConfigProduction"   -> Forms.boolean
      )(CreateAppConfigsForm.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying("No update requested", form =>
          Seq(
            form.appConfigBase,
            form.appConfigDevelopment,
            form.appConfigQA,
            form.appConfigStaging,
            form.appConfigProduction
          ).contains(true)
        )
    )
