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

package uk.gov.hmrc.cataloguefrontend.jsondata

object ServiceDependencies {


  val groupArtefactsFromHMRC: String =
    """[
        {"group":"uk.gov.hmrc","artefacts":["bootstrap-backend-play-28","bootstrap-common-play-28","bootstrap-frontend-play-28","bootstrap-health-play-28","crypto","crypto-json-play-28"]},
        {"group":"am.gov.vetinari","artefacts":["wuffles-dog"]}
       ]"""

  // SlugName is
  val serviceDepsForBootstrapBackendPlay: String =
    """[
         {"slugName":"personal-details-validation-frontend","slugVersion":"0.30.0","teams":["Verification"],"depGroup":"org.slf4j","depArtefact":"slf4j-api","depVersion":"1.7.22","scopes":["compile"]},
         {"slugName":"mobile-auth-stub","slugVersion":"0.12.0","teams":["NGC"],"depGroup":"org.slf4j","depArtefact":"slf4j-api","depVersion":"1.7.25","scopes":["compile"]}
       ]"""
}
