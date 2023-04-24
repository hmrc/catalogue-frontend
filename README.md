# catalogue-frontend

![](https://img.shields.io/github/v/release/hmrc/catalogue-frontend)

* [Setup](#setup)
* [Updating the front page](#updating-the-front-page)
* [Tests](#tests)
* [License](#license)

## Setup

See [catalogue-acceptance-tests]("https://github.com/hmrc/catalogue-acceptance-tests") for help setting up dependent services.

## Updating the front page

Blog posts are populated via a call to Confluence which searches by the configured label `confluence.search.label`.

## Tests

Please run tests with any work changes
`$ sbt test`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
