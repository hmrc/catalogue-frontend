# catalogue-frontend

![](https://img.shields.io/github/v/release/hmrc/catalogue-frontend)

* [Setup](#setup)
* [Tests](#tests)
* [License](#license)

### Setup

Make sure your github token are set up [properly](https://github.com/hmrc/service-configs#setting-up-github-tokens-locally-required-for-viewing-bobby-rules)
for service-dependency to work properly.

### Updating the recent version history and blog posts on the front page

The front page of the Catalogue has two sections for displaying the most recent significant changes and blog posts.

These are manually curated lists, rendered from two markdown files in this repo:
  - [`VERSION_HISTORY.md`](conf/VERSION_HISTORY.md)
  - [`BLOG_POSTS.md`](conf/BLOG_POSTS.md)

They support Github flavour markdown.

Two config variables define how many lines of the file to render:

```
whats-new.display.lines = 30 #How many lines of the VERSION_HISTORY.md to render on the front page
blog-posts.display.lines = 80 #How many lines of the BLOG_POSTS.md to render on the front page
```

> Note that these control how many lines are rendered in total (which includes the table header), in the expanded view.
> This has no bearing on how many are shown when the box is collapsed, which is controlled by the height of the div via css.

### Tests
Please run tests with any work changes
`$ sbt test`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
