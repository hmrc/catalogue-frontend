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

given Parser[TestType] = Parser.parser(TestType.values)

enum TestType(
  override val asString: String
) extends FromString
  derives Ordering, FormFormat:
  case UITest          extends TestType("UI Journey Test" )
  case APITest         extends TestType("API Test"        )
  case PerformanceTest extends TestType("Performance Test")