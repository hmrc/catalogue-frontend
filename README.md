# catalogue-frontend

![](https://img.shields.io/github/v/release/hmrc/catalogue-frontend)

* [Setup](#setup)
  * [GitHub Token](#github-token)
  * [AWS Credentials](#aws-credentials)
* [Tests](#tests)
* [License](#license)

## Setup

### GitHub Token

Make sure your GitHub token are set up [properly](https://github.com/hmrc/service-configs#setting-up-github-tokens-locally-required-for-viewing-bobby-rules)
for service-dependency to work properly.

### AWS Credentials

In order to run two of the unit tests in `uk.gov.hmrc.cataloguefrontend.PrototypePageSpec` you need to 
have an AWS default profile with dummy credentials.  This means you need the following

* Default entry in `$HOME/.aws/config`

```
[profile default]
region = eu-west-2
output = JSON
```

* Credentials for that profile in `$HOME/.aws/credentials` (the values used for keys don't matter)

``` 
[default]
aws_access_key_id = 1111
aws_secret_access_key = 2222
```


## Updating the front page

Blog posts are populated via a call to Confluence which searches by the configured label `confluence.search.label`.

## Tests

Please run tests with any work changes
`$ sbt test`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
