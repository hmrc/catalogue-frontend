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

package uk.gov.hmrc.cataloguefrontend.createrepository

import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}

enum CreateServiceRepositoryType(val asString: String) extends FromString:
  case Empty                            extends CreateServiceRepositoryType("Empty"                                )
  case FrontendMicroservice             extends CreateServiceRepositoryType("Frontend microservice"                )
  case FrontendMicroserviceWithScaffold extends CreateServiceRepositoryType("Frontend microservice - with scaffold")
  case FrontendMicroserviceWithMongodb  extends CreateServiceRepositoryType("Frontend microservice - with mongodb" )
  case BackendMicroservice              extends CreateServiceRepositoryType("Backend microservice"                 )
  case BackendMicroserviceWithMongodb   extends CreateServiceRepositoryType("Backend microservice - with mongodb"  )
  case ApiMicroservice                  extends CreateServiceRepositoryType("API microservice"                     )
  case ApiMicroserviceWithMongodb       extends CreateServiceRepositoryType("API microservice - with mongodb"      )

object CreateServiceRepositoryType extends FromStringEnum[CreateServiceRepositoryType]{
  val parsingError: String =
    s"Not a valid CreateServiceRepositoryType. Should be one of ${values.mkString(", ")}"
}
