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

package uk.gov.hmrc.cataloguefrontend

import cats.data.EitherT
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.utils.UriEncoding
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.model.DependencyScope
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.DependenciesPage
import views.html.dependencies.DependencyGraphs

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependenciesController @Inject() (
  override val mcc        : MessagesControllerComponents,
  dependenciesService     : DependenciesService,
  whatsRunningWhereService: WhatsRunningWhereService,
  dependenciesPage        : DependenciesPage,
  graphsPage              : DependencyGraphs,
  override val auth       : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def services(name: ServiceName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        deployments         <- whatsRunningWhereService.releasesForService(name).map(_.versions)
        serviceDependencies <- dependenciesService.search(name, deployments)
      yield Ok(dependenciesPage(name, serviceDependencies.sortBy(_.version)(Ordering[Version].reverse)))
    }

  def service(name: ServiceName, version: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      dependenciesService.getServiceDependencies(name, Version(version))
        .map(maybeDeps => Ok(dependenciesPage(name, maybeDeps.toSeq)))
    }

  def graphs(name: ServiceName, version: String, scope: String): Action[AnyContent] =
    BasicAuthAction.async { implicit  request =>
      (for
         scope        <- EitherT.fromEither[Future](DependencyScope.parse(scope))
                           .leftMap(_ => BadRequest(s"Invalid scope $scope"))
         dependencies <- EitherT.fromOptionF(
                           dependenciesService.getServiceDependencies(name, Version(version)),
                           NotFound(s"No dependency data available for $name:$version:$scope")
                         )
       yield
         Ok(graphsPage(
           name,
           dependencies.dotFileForScope(scope).map(d => UriEncoding.encodePathSegment(d, "UTF-8")),
           scope
         ))
      ).merge
    }

end DependenciesController
