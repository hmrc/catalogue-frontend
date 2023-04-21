# catalogue-frontend

![](https://img.shields.io/github/v/release/hmrc/catalogue-frontend)

* [Setup](#setup)
  * [GitHub Token](#github-token)
* [Tests](#tests)
* [License](#license)

## Setup

### GitHub Token

Make sure your GitHub token are set up [properly](https://github.com/hmrc/service-configs#setting-up-github-tokens-locally-required-for-viewing-bobby-rules)
for service-dependency to work properly.



## Updating the front page

Blog posts are populated via a call to Confluence which searches by the configured label `confluence.search.label`.

## Tests

Please run tests with any work changes
`$ sbt test`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
