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


import play.api.data.{Form, Forms}
import play.api.data.Forms.{boolean, mapping, nonEmptyText, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Writes, __}
import uk.gov.hmrc.cataloguefrontend.createrepository.CreateRepoConstraints.mkConstraint
import uk.gov.hmrc.cataloguefrontend.model.TeamName

case class CreateServiceRepoForm(
  repositoryName: String,
  makePrivate   : Boolean,
  teamName      : TeamName,
  repoType      : String
)

object CreateServiceRepoForm:
  val writes: Writes[CreateServiceRepoForm] =
    ( (__ \ "repositoryName").write[String]
    ~ (__ \ "makePrivate"   ).write[Boolean]
    ~ (__ \ "teamName"      ).write[TeamName](TeamName.format)
    ~ (__ \ "repoType"      ).write[String]
    )(r => Tuple.fromProductTyped(r))

  val repoTypeValidation: String => Boolean =
    str => CreateServiceRepositoryType.parse(str).isRight

  val conflictingFieldsValidation1 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("backend")  && crf.repositoryName.toLowerCase.contains("frontend"))
  val conflictingFieldsValidation2 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && crf.repositoryName.toLowerCase.contains("backend"))
  val frontendValidation1          : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && !crf.repositoryName.toLowerCase.contains("frontend"))
  val frontendValidation2          : CreateServiceRepoForm => Boolean = crf => !(crf.repositoryName.toLowerCase.contains("frontend") && !crf.repoType.toLowerCase.contains("frontend"))


  private val repoTypeConstraint: Constraint[String] =
    mkConstraint("constraints.repoTypeCheck")(constraint = repoTypeValidation, error = CreateServiceRepositoryType.parsingError)

  private val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = conflictingFieldsValidation1, error = "You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidation2, error = "You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation1, error = "Repositories with a frontend repo type require 'frontend' to be present in their repo name."),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation2, error = "Repositories with 'frontend' in their repo name require a frontend repo type")
  )

  val form: Form[CreateServiceRepoForm] =
    Form(
      mapping(
        "repositoryName" -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(47, None)*),
        "makePrivate"    -> boolean,
        "teamName"       -> Forms.of[TeamName](TeamName.formFormat),
        "repoType"       -> nonEmptyText.verifying(repoTypeConstraint),
      )(CreateServiceRepoForm.apply)(r => Some(Tuple.fromProductTyped(r)))
        .verifying(repoTypeAndNameConstraints*)
    )

end CreateServiceRepoForm

object CreateTestRepoForm:
  private val repoTestTypeValidation: String => Boolean =
    str => CreateTestRepositoryType.parse(str).isRight

  private val repoTestTypeConstraint: Constraint[String] =
    mkConstraint("constraints.repoTypeCheck")(constraint = repoTestTypeValidation, error = CreateTestRepositoryType.parsingError)

  private[createrepository] val repoNameTestConstraint: CreateServiceRepoForm => Boolean = crf => crf.repositoryName.toLowerCase.endsWith("-tests") || crf.repositoryName.toLowerCase.endsWith("-test")

  private val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = repoNameTestConstraint, error = "Repository name can only end in '-test' or '-tests'"),
  )

  val form: Form[CreateServiceRepoForm] =
    Form(
      mapping(
        "repositoryName" -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(47, None)*),
        "makePrivate"    -> boolean,
        "teamName"       -> Forms.of[TeamName](TeamName.formFormat),
        "repoType"       -> nonEmptyText.verifying(repoTestTypeConstraint),
      )(CreateServiceRepoForm.apply)(r => Some(Tuple.fromProductTyped(r)))
        .verifying(repoTypeAndNameConstraints*)
    )
end CreateTestRepoForm

case class CreatePrototypeRepoForm(
  repositoryName: String,
  password      : String,
  teamName      : TeamName,
  slackChannels : String
)

object CreatePrototypeRepoForm:
  val writes: Writes[CreatePrototypeRepoForm] =
    ( (__ \ "repositoryName").write[String]
    ~ (__ \ "password"      ).write[String]
    ~ (__ \ "teamName"      ).write[String].contramap[TeamName](_.asString)
    ~ (__ \ "slackChannels" ).write[String]
    )(r => Tuple.fromProductTyped(r))

  private val passwordCharacterValidation: String => Boolean =
    str => str.matches("^[a-zA-Z0-9_]+$")

  private val passwordConstraint =
    mkConstraint("constraints.passwordCharacterCheck")(constraint = passwordCharacterValidation, error = "Should only contain the following characters uppercase letters, lowercase letters, numbers, underscores")

  private val slackChannelCharacterValidation: String => Boolean =
    str => str.matches("^#?[a-z0-9-_]*$")

  private val slackChannelLengthValidation   : String => Boolean =
    str => str.isEmpty || !str.split(',').exists(elem => elem.length > 80)

  private val slackChannelConstraint = Seq(
    mkConstraint("constraints.channelLengthCheck")(constraint = slackChannelLengthValidation, error = "Each slack channel name must be under 80 characters long"),
    mkConstraint("constraints.channelCharacterCheck")(constraint = slackChannelCharacterValidation, error = "Each slack channel name Should only contain the following characters lowercase letters, numbers, underscores, dashes, hash character (#)")
  )

  val form: Form[CreatePrototypeRepoForm] =
    Form(
      mapping(
        "repositoryName"      -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(30, Some("-prototype"))*),
        "password"            -> nonEmptyText.verifying(passwordConstraint),
        "teamName"            -> Forms.of[TeamName](TeamName.formFormat),
        "slackChannels"       -> text.verifying(slackChannelConstraint*),
      )(CreatePrototypeRepoForm.apply)(r => Some(Tuple.fromProductTyped(r)))
    )

end CreatePrototypeRepoForm

object CreateRepoConstraints:

  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  def createRepoNameConstraints(length: Int, suffix: Option[String]): Seq[Constraint[String]] =
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
