package uk.gov.hmrc.cataloguefrontend.metrics.views

import uk.gov.hmrc.cataloguefrontend.metrics.model.{DependencyName, GroupName, RepositoryName}

final case class SearchForm(
                             group: Option[GroupName],
                             dependency: Option[DependencyName],
                             repository: Option[RepositoryName]
                           )

object SearchForm {
  def applyRaw(
                group: Option[String],
                dependency: Option[String],
                repository: Option[String]
              ): SearchForm = SearchForm.apply(
    group.map(GroupName.apply),
    dependency.map(DependencyName.apply),
    repository.map(RepositoryName.apply)
  )

  def unapplyRaw(searchForm: SearchForm): Option[(Option[String], Option[String], Option[String])] = unapply(searchForm).map{ case(g, d, r) =>
    (g.map(_.value), d.map(_.value), r.map(_.value))
  }
}
