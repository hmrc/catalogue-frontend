/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.actions

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.{ routes => appRoutes }
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, User}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.service.CatalogueErrorHandler
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

final case class UmpAuthenticatedRequest[A](
    request    : Request[A]
  , token      : UmpToken
  , user       : User
  , displayName: DisplayName
  ) extends WrappedRequest(request)


/** Creates an Action will only proceed to invoke the action body, if there is a valid [[UmpToken]] in session.
  * If there isn't, it will short circuit with a Redirect to SignIn page.
  *
  * Use [[VerifySignInStatus]] Action if you want to know if there is a valid token, but it should not terminate invocation.
  */
@Singleton
class UmpAuthActionBuilder @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  cc                         : MessagesControllerComponents,
  catalogueErrorHandler      : CatalogueErrorHandler
)(implicit val ec: ExecutionContext) {

  val whenAuthenticated =
    withCheck(None)

  def withGroup(group: String) =
    withCheck(optGroup = Some(group))

 private def withCheck(optGroup: Option[String]) =
    new ActionBuilder[UmpAuthenticatedRequest, AnyContent]
     with ActionRefiner[Request, UmpAuthenticatedRequest] {

      def refine[A](request: Request[A]): Future[Either[Result, UmpAuthenticatedRequest[A]]] = {
        implicit val hc: HeaderCarrier =
          HeaderCarrierConverter.fromRequestAndSession(request, request.session)

        val signInPage =
          appRoutes.AuthController.showSignInPage(targetUrl =
            Some(request.target.uriString).filter(_ => request.method == "GET")
          )

        def fromSession(key: String) =
          EitherT.fromOption[Future](request.session.get(key)
            , Redirect(signInPage)
            )

        (for {
           token       <- fromSession(UmpToken.SESSION_KEY_NAME).map(UmpToken.apply)
           displayName <- fromSession(DisplayName.SESSION_KEY_NAME).map(DisplayName.apply)
           user        <- EitherT.fromOptionF[Future, Result, User](userManagementAuthConnector.getUser(token)
                            , Redirect(signInPage)
                            )
           _           <- if (optGroup.map(user.groups.contains(_)).getOrElse(true))
                            EitherT.pure[Future, Result](())
                          else
                            EitherT.left[Result](Future(Forbidden(catalogueErrorHandler.forbiddenTemplate(request))))
         } yield UmpAuthenticatedRequest(
             request
           , token       = token
           , user        = user
           , displayName = displayName
           )
        ).value
      }

      override def parser: BodyParser[AnyContent] = cc.parsers.anyContent

      override protected def executionContext: ExecutionContext = cc.executionContext
    }
}
