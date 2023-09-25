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

sealed trait CreateServiceRepositoryType { def asString: String}

object CreateServiceRepositoryType {
  case object Empty                            extends CreateServiceRepositoryType { override def asString: String = "Empty"}
  case object FrontendMicroservice             extends CreateServiceRepositoryType { override def asString: String = "Frontend microservice"}
  case object FrontendMicroserviceWithScaffold extends CreateServiceRepositoryType { override def asString: String = "Frontend microservice - with scaffold" }
  case object FrontendMicroserviceWithMongodb  extends CreateServiceRepositoryType { override def asString: String = "Frontend microservice - with mongodb" }
  case object BackendMicroservice              extends CreateServiceRepositoryType { override def asString: String = "Backend microservice"}
  case object BackendMicroserviceWithMongodb   extends CreateServiceRepositoryType { override def asString: String = "Backend microservice - with mongodb"}
  case object ApiMicroservice                  extends CreateServiceRepositoryType { override def asString: String = "API microservice"}
  case object ApiMicroserviceWithMongodb       extends CreateServiceRepositoryType { override def asString: String = "API microservice - with mongodb"}

  val values = List(
    Empty,
    FrontendMicroservice,
    FrontendMicroserviceWithScaffold,
    FrontendMicroserviceWithMongodb,
    BackendMicroservice,
    BackendMicroserviceWithMongodb,
    ApiMicroservice,
    ApiMicroserviceWithMongodb
  )

  val parsingError = s"Not a valid CreateServiceRepositoryType. Should be one of ${values.mkString(", ")}"

  def parse(str: String): Option[CreateServiceRepositoryType] = values.find(_.asString == str)

}
