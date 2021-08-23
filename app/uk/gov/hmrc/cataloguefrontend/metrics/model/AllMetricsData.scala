package uk.gov.hmrc.cataloguefrontend.metrics.model

final case class AllMetricsData(groups: Seq[Group], repositories: Seq[Repository], dependencies: Seq[DependencyName])

object AllMetricsData {
  def apply(metricsResponse: MetricsResponse): AllMetricsData = {

    AllMetricsData(
      groups = Group(metricsResponse.metrics),
      repositories = Repository(metricsResponse.metrics),
      dependencies = DependencyName(metricsResponse.metrics)
    )
  }
}
