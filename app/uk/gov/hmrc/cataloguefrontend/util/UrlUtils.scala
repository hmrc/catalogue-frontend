/*
 * Copyright 2020 HM Revenue & Customs
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

import java.net.URLEncoder

import play.utils.UriEncoding

object UrlUtils {
  def encodeQueryParam(param: String): String =
    URLEncoder.encode(param, "UTF-8")

  def encodePathParam(param: String): String =
    UriEncoding.encodePathSegment(param, "UTF-8")

  def toQueryParams(params: Seq[(String, String)]): String =
    params
      .map { case (k, v) =>
        encodeQueryParam(k) + "=" + encodeQueryParam(v)
      }.mkString("&")
}