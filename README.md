# catalogue-frontend

[![Build Status](https://travis-ci.org/hmrc/catalogue-frontend.svg?branch=master)](https://travis-ci.org/hmrc/catalogue-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/catalogue-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/catalogue-frontend/_latestVersion)

* [Setup](#setup)
* [Frontend Development](#frontend-development)
* [Tests](#tests)
* [License](#license)

### Setup

### Frontend Development

#### Requirements

* [Node.js](https://nodejs.org/en/)
* [npm](https://www.npmjs.com/)

#### [Node.js](https://nodejs.org/en/) & [npm](https://www.npmjs.com/)

To install multiple versions of Node.js, you may find it easier to use a node version manager:

* [nvm](https://github.com/creationix/nvm)
* [n](https://github.com/tj/n)

#### Install dependencies

`$ npm install`

#### Watch CSS

To generate the css with sourcemaps run:
`$ npm run css:watch`

#### Minify css

To generate and minfiy the css with run:
`$ npm run css`

### Tests
Please run tests with any work changes
`$ sbt test`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
