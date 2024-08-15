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

import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}

import FromStringEnum._

given Parser[ServiceType] = Parser.parser(ServiceType.values)

enum ServiceType(
  override val asString: String
) extends FromString
  derives Ordering, FormFormat:
  case Empty                            extends ServiceType("Empty"                                )
  case FrontendMicroservice             extends ServiceType("Frontend microservice"                )
  case FrontendMicroserviceWithScaffold extends ServiceType("Frontend microservice - with scaffold")
  case FrontendMicroserviceWithMongodb  extends ServiceType("Frontend microservice - with mongodb" )
  case BackendMicroservice              extends ServiceType("Backend microservice"                 )
  case BackendMicroserviceWithMongodb   extends ServiceType("Backend microservice - with mongodb"  )
  case ApiMicroservice                  extends ServiceType("API microservice"                     )
  case ApiMicroserviceWithMongodb       extends ServiceType("API microservice - with mongodb"      )
