# catalogue-frontend

![](https://img.shields.io/github/v/release/hmrc/catalogue-frontend)

* [Setup](#setup)
* [Development](#development)
* [Updating the front page](#updating-the-front-page)
* [Tests](#tests)
* [License](#license)

## Setup

For help setting up dependent services see [catalogue-acceptance-tests]("https://github.com/hmrc/catalogue-acceptance-tests")

## Updating the front page

Blog posts are populated via a call to Confluence which searches by the configured label `confluence.search.label`.

## Vulnerability views preview

Existing vulnerability views remain the default. Enable the preview controls with
`-Dfeature.vulnerability-view-preview=true` (disabled by default). Feedback goes to
`vulnerability-view.feedback-url`, which defaults to the PlatOps Slack channel.

`POST /preferences/vulnerability-view` accepts form fields `view=preview|current`
and an optional relative `returnTo`. Preview selection sets the separate
`catalogue-vulnerability-view` cookie for 30 days from selection; returning to
current views removes it. Reading pages does not renew the cookie. Disabling the
switch overrides any stored preference.

Service, team and deployment controllers resolve `VulnerabilityView.select(request)`
and pass the result to their templates. The service controller selects the separate
`ServiceInfoPreviewPage` template when opted in; it initially reuses the current
page content with preview controls. Page integrations should branch on
`isPreview` before calling a v2 API; current requests must retain their existing API
calls. New panels and v2 API integration are outside this change.

Users choose their preference on service or team pages before entering the existing
deployment wizard. Deployment links go directly to the wizard. The wizard and POST
review respect the preference and show preview status and feedback without a
preference form, so switching views cannot discard or resubmit deployment form data.

## Tests

Please run tests with any work changes
`$ sbt test`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
