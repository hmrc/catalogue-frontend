/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.util

import play.api.libs.json.{JsError, JsResult, JsSuccess}


object CategoryHelper:
  given cats.Applicative[JsResult] =
    new cats.Applicative[JsResult]:
      def pure[A](a: A): JsResult[A] =
        JsSuccess(a)

      def ap[A, B](ff: JsResult[A => B])(fa: JsResult[A]): JsResult[B] =
        fa match
          case JsSuccess(a, p1) =>
            ff match
              case JsSuccess(f, p2) => JsSuccess(f(a), p1)
              case JsError(e1)      => JsError(e1)
          case JsError(e1) => JsError(e1)
