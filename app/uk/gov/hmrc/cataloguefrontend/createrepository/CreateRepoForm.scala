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


import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Writes, __}
import uk.gov.hmrc.cataloguefrontend.createrepository.CreateRepoConstraints.mkConstraint

case class CreateServiceRepoForm(
  repositoryName: String,
  makePrivate   : Boolean,
  teamName      : String,
  repoType      : String
)

object CreateServiceRepoForm {
  implicit val writes: Writes[CreateServiceRepoForm] =
    ( (__ \ "repositoryName").write[String]
    ~ (__ \ "makePrivate"   ).write[Boolean]
    ~ (__ \ "teamName"      ).write[String]
    ~ (__ \ "repoType"      ).write[String]
    )(unlift(CreateServiceRepoForm.unapply))

  val repoTypeValidation: String => Boolean =
    str => CreateServiceRepositoryType.parse(str).nonEmpty

  val conflictingFieldsValidation1 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("backend")  && crf.repositoryName.toLowerCase.contains("frontend"))
  val conflictingFieldsValidation2 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && crf.repositoryName.toLowerCase.contains("backend"))
  val frontendValidation1          : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && !crf.repositoryName.toLowerCase.contains("frontend"))
  val frontendValidation2          : CreateServiceRepoForm => Boolean = crf => !(crf.repositoryName.toLowerCase.contains("frontend") && !crf.repoType.toLowerCase.contains("frontend"))


  private val repoTypeConstraint: Constraint[String] = mkConstraint("constraints.repoTypeCheck")(constraint = repoTypeValidation, error = CreateServiceRepositoryType.parsingError)

  private val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = conflictingFieldsValidation1, error = "You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidation2, error = "You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation1, error = "Repositories with a frontend repo type require 'frontend' to be present in their repo name."),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation2, error = "Repositories with 'frontend' in their repo name require a frontend repo type")
  )

  val form: Form[CreateServiceRepoForm] =
    Form(
      mapping(
        "repositoryName" -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(47, None) :_*),
        "makePrivate"    -> boolean,
        "teamName"       -> nonEmptyText,
        "repoType"       -> nonEmptyText.verifying(repoTypeConstraint),
      )(CreateServiceRepoForm.apply)(CreateServiceRepoForm.unapply)
        .verifying(repoTypeAndNameConstraints :_*)
    )
}

object CreateTestRepoForm {
  private val repoTestTypeValidation: String => Boolean =
    str => CreateTestRepositoryType.parse(str).nonEmpty

  private val repoTestTypeConstraint: Constraint[String] =
    mkConstraint("constraints.repoTypeCheck")(constraint = repoTestTypeValidation, error = CreateTestRepositoryType.parsingError)

  val conflictingFieldsValidationUiTests         : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.startsWith("ui") && !crf.repositoryName.toLowerCase.endsWith("-ui-tests"))
  val conflictingFieldsValidationApiTests        : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.startsWith("api") && !crf.repositoryName.toLowerCase.endsWith("-api-tests"))
  val conflictingFieldsValidationPerformanceTests: CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.startsWith("performance") && !crf.repositoryName.toLowerCase.endsWith("-performance-tests"))

  private val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = conflictingFieldsValidationUiTests, error = "You have chosen a ui test repo type, but the name doesn't end 'ui-tests'. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidationApiTests, error = "You have chosen an api test repo type, but the repo name doesn't end 'api-tests'. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidationPerformanceTests, error = "You have chosen a performance test repo type, but the repo name doesn't end 'performance-tests'. Change either the repo name or repo type")
  )

  val form: Form[CreateServiceRepoForm] = Form(
    mapping(
      "repositoryName" -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(47, None): _*),
      "makePrivate" -> boolean,
      "teamName" -> nonEmptyText,
      "repoType" -> nonEmptyText.verifying(repoTestTypeConstraint),
    )(CreateServiceRepoForm.apply)(CreateServiceRepoForm.unapply)
      .verifying(repoTypeAndNameConstraints: _*)
  )
}

case class CreatePrototypeRepoForm(
  repositoryName: String,
  password      : String,
  teamName      : String,
  slackChannels : String
)

object CreatePrototypeRepoForm {

  implicit val writes: Writes[CreatePrototypeRepoForm] =
    ( (__ \ "repositoryName"  ).write[String]
    ~ (__ \ "password"      ).write[String]
    ~ (__ \ "teamName"      ).write[String]
    ~ (__ \ "slackChannels" ).write[String]
    )(unlift(CreatePrototypeRepoForm.unapply))

  private val passwordCharacterValidation: String => Boolean =
    str => str.matches("^[a-zA-Z0-9_]+$")

  private val passwordConstraint =
    mkConstraint("constraints.passwordCharacterCheck")(constraint = passwordCharacterValidation, error = "Should only contain the following characters uppercase letters, lowercase letters, numbers, underscores")

  private val slackChannelCharacterValidation: String => Boolean = str => str.matches("^#?[a-z0-9-_]*$")
  private val slackChannelLengthValidation   : String => Boolean = str => str.isEmpty || !str.split(',').exists(elem => elem.length > 80)

  private val slackChannelConstraint = Seq(
    mkConstraint("constraints.channelLengthCheck")(constraint = slackChannelLengthValidation, error = "Each slack channel name must be under 80 characters long"),
    mkConstraint("constraints.channelCharacterCheck")(constraint = slackChannelCharacterValidation, error = "Each slack channel name Should only contain the following characters lowercase letters, numbers, underscores, dashes, hash character (#)")
  )

  val form: Form[CreatePrototypeRepoForm] =
    Form(
      mapping(
        "repositoryName"      -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(30, Some("-prototype")) :_*),
        "password"            -> nonEmptyText.verifying(passwordConstraint),
        "teamName"            -> nonEmptyText,
        "slackChannels"       -> text.verifying(slackChannelConstraint :_*),
      )(CreatePrototypeRepoForm.apply)(CreatePrototypeRepoForm.unapply)
    )
}

object CreateRepoConstraints {

  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName)(toBeValidated => if (constraint(toBeValidated)) Valid else Invalid(error))

  def createRepoNameConstraints(length: Int, suffix: Option[String]): Seq[Constraint[String]] = {
    val whiteSpaceValidation: String => Boolean = str => !str.matches(".*\\s.*")
    val underscoreValidation: String => Boolean = str => !str.contains("_")
    val slashValidation     : String => Boolean = str => !str.contains("/")
    val lengthValidation    : String => Boolean = str => str.length <= length
    val lowercaseValidation : String => Boolean = str => str.toLowerCase.equals(str)
    val suffixValidation    : String => Boolean = str => if (suffix.isEmpty) true else str.endsWith(suffix.get)

    Seq(
      mkConstraint("constraints.repoNameWhitespaceCheck")(constraint = whiteSpaceValidation, error = "Repository name cannot include whitespace, use hyphens instead"),
      mkConstraint("constraints.repoNameUnderscoreCheck")(constraint = underscoreValidation, error = "Repository name cannot include underscores, use hyphens instead"),
      mkConstraint("constraints.repoNameSlashCheck"     )(constraint = slashValidation     , error = "Repository name cannot include forward slashes. You do not need to specify the hmrc organisation"),
      mkConstraint("constraints.repoNameLengthCheck"    )(constraint = lengthValidation    , error = s"Repository name can have a maximum of $length characters"),
      mkConstraint("constraints.repoNameCaseCheck"      )(constraint = lowercaseValidation , error = "Repository name should only contain lowercase characters"),
      mkConstraint("constraints.repoNameSuffixCheck"    )(constraint = suffixValidation    , error = s"Repository name must end with ${if (suffix.nonEmpty) suffix.get else ""}")
    )
  }
}
