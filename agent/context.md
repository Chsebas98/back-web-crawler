You are working on the BACKEND.

## Project context

The goal is build a web crawler/scraper that extracts the first 30 entries from:

https://news.ycombinator.com/

For every entry, we only care about:

- number/rank
- title
- points
- number of comments

The backend is implemented using:

- Java
- Spring Boot
- Spring WebFlux
- Reactive programming
- PostgreSQL
- Spring Data R2DBC
- Maven or Gradle depending on the existing project setup
- JUnit 5
- Reactor Test / WebTestClient for automated tests

The frontend is a separate React + TypeScript + Vite application. The backend must expose a clean REST API for the frontend.

IMPORTANT:
This is an interview coding exercise. Prioritize clean, understandable, maintainable code over unnecessary complexity.

The code should be easy to explain during a technical interview.

---

# Functional requirements

The application must scrape the first 30 Hacker News entries from:

https://news.ycombinator.com/

For each entry extract:

- number
- title
- points
- comments

Example conceptual model:

{
  "number": 1,
  "title": "Example title",
  "points": 123,
  "comments": 45
}

The crawler should then support two filtering operations.

## Filter 1

Return all entries whose title contains MORE THAN 5 words.

Sort the resulting entries by number of comments, descending.

Conceptually:

GET /api/stories?filter=more-than-five-words

Expected behavior:

1. Crawl/fetch the first 30 Hacker News stories.
2. Count words according to the specific exercise definition.
3. Keep stories with more than 5 words.
4. Sort by comments descending.
5. Return the result.

## Filter 2

Return all entries whose title contains FEWER THAN OR EQUAL TO 5 words.

Sort the resulting entries by points, descending.

Conceptually:

GET /api/stories?filter=five-or-fewer-words

Expected behavior:

1. Crawl/fetch the first 30 Hacker News stories.
2. Count words according to the exercise definition.
3. Keep stories with <= 5 words.
4. Sort by points descending.
5. Return the result.

---

# Word-counting rule

This requirement is important and must be implemented explicitly and tested.

When counting words:

- Count only space-separated words.
- Symbols/punctuation should not count as separate words.
- A punctuation/symbol attached to a word should not create an additional word.
- Do not count symbols as words.

Example:

"This is - a self-explained example"

must be counted as 5 words:

1. This
2. is
3. a
4. self-explained
5. example

The "-" token must not count as a word.

Avoid using an overly complicated natural-language tokenizer.

Implement a small deterministic utility/service responsible for this behavior.

The word-counting logic must have unit tests covering:
- normal titles
- punctuation
- standalone symbols
- hyphenated words
- multiple spaces
- leading/trailing spaces
- empty/null titles if appropriate

---

# Scraping requirements

Use a proper HTTP client compatible with Spring WebFlux.

Prefer Spring WebClient.

Do NOT use blocking HTTP clients such as RestTemplate.

The crawler should:

1. Request https://news.ycombinator.com/
2. Parse the HTML response.
3. Extract the first 30 story rows.
4. Extract:
   - story rank
   - title
   - points
   - comments count

Use an appropriate HTML parsing library compatible with the project, such as Jsoup.

Keep the scraper isolated from the business logic.

Recommended separation:

HackerNewsClient / HackerNewsScraper
    ↓
Story service
    ↓
Filtering/sorting logic
    ↓
Usage tracking
    ↓
REST controller

Do not put HTML parsing directly inside the controller.

---

# Important scraping considerations

Hacker News HTML has a specific structure.

Do not make the rest of the application depend directly on Jsoup DOM structures.

The scraper should convert HTML into a domain representation.

For example:

HackerNewsScraper
    -> Mono<List<Story>>

The rest of the application should only know about Story objects.

The scraper should fail gracefully if:
- Hacker News is unavailable
- the response is malformed
- an expected HTML element is missing
- fewer than 30 stories are returned

Do not silently return corrupted data.

Use meaningful exceptions and appropriate error handling.

Avoid swallowing exceptions.

---

# Reactive requirements

The backend uses Spring WebFlux, so maintain a reactive architecture.

Avoid:
- blocking calls
- .block()
- .subscribe() inside application business logic
- synchronous JDBC
- RestTemplate

Use:

Mono
Flux
WebClient
R2DBC

The controller should return reactive types.

For example:

Mono<ResponseEntity<...>>
or
Flux<...>

Do not introduce blocking behavior just to simplify implementation.

---

# Domain design

Create a clear domain model.

A possible Story model:

Story:
- Integer number
- String title
- Integer points
- Integer comments

Avoid exposing persistence entities directly through the REST API if the design benefits from DTOs.

Consider having:

domain/
application/
infrastructure/
web/

or another clean organization appropriate for a small Spring Boot application.

Do not over-engineer the project with unnecessary layers.

---

# Usage tracking requirement

The exercise explicitly requires storing usage data.

At minimum, every request that applies a filter must store:

- request timestamp
- applied filter

Additional useful fields may include:

- request ID
- endpoint
- execution duration
- number of stories scraped
- number of stories returned
- success/failure
- error message if applicable

PostgreSQL should be used as the persistence mechanism.

Create an appropriate table, for example:

crawler_usage

Possible columns:

id
request_timestamp
filter
execution_time_ms
stories_scraped
stories_returned
status
error_message

Use an appropriate timestamp type.

The usage tracking must be non-blocking and use R2DBC.

Do not use JPA/Hibernate/JDBC in the WebFlux application.

---

# Usage tracking semantics

Every filtering API request should produce a usage record.

The usage record should identify which filter was applied.

For example:

filter = MORE_THAN_FIVE_WORDS

or

filter = FIVE_OR_FEWER_WORDS

Use an enum internally instead of relying on arbitrary strings throughout the codebase.

The request timestamp should be generated by the backend.

Do not trust the frontend to provide the timestamp.

If tracking execution duration, measure it in the backend.

Think carefully about failure behavior.

If scraping fails, the application may still record a usage entry with:

status = ERROR

and an appropriate error message.

Do not allow usage tracking failures to unnecessarily corrupt the main crawler behavior unless there is a strong reason to do so.

Document the chosen behavior.

---

# API design

Expose a simple REST API.

Recommended:

GET /api/stories?filter=MORE_THAN_FIVE_WORDS

GET /api/stories?filter=FIVE_OR_FEWER_WORDS

You may use a different API design if there is a strong architectural reason, but keep it simple.

The API response should be JSON.

Example:

[
  {
    "number": 7,
    "title": "Some example story title",
    "points": 150,
    "comments": 83
  }
]

The API should validate the filter parameter.

Invalid filters should return an appropriate 4xx response.

Do not silently fall back to a default filter.

---

# CORS

The frontend is a separate React/Vite application.

Configure CORS appropriately for local development.

Expected frontend development origin will likely be:

http://localhost:5173

Do not use wildcard CORS in production-oriented configuration if avoidable.

Make the allowed origin configurable.

For example:

CORS_ALLOWED_ORIGINS=http://localhost:5173

---

# Configuration

Externalize configuration using application.yml/application.properties and environment variables.

At minimum:

- Hacker News base URL
- PostgreSQL connection
- CORS allowed origins
- HTTP timeout if applicable

Do not hardcode credentials.

Provide an example configuration file such as:

application-local.yml
or
.env.example

Never commit real secrets.

---

# Database

Use PostgreSQL.

Use migrations rather than relying on automatic schema generation.

Flyway or Liquibase can be used if compatible with the chosen architecture.

Be careful:
Spring Data R2DBC is reactive, while traditional Flyway database migrations generally use JDBC.

It is acceptable to use Flyway only for startup migrations while using R2DBC for runtime application access, but document the decision.

Alternatively, use another lightweight migration approach if already present in the project.

Create the usage tracking table through a migration.

---

# Testing requirements

Automated testing is important because the interview explicitly mentions testing.

Create tests for at least:

## Word counter

Test:

"This is a title"

"This is - a self-explained example"

"Hello, world!"

"One  two   three"

"---"

""

"   "

"self-explained title"

Make sure the exercise's exact word-counting semantics are respected.

## Filtering

Given a list of Story objects:

Test MORE_THAN_FIVE_WORDS:
- filters correctly
- excludes exactly 5 words
- sorts by comments descending

Test FIVE_OR_FEWER_WORDS:
- includes exactly 5 words
- filters correctly
- sorts by points descending

Test tie behavior if relevant.

Use deterministic test data.

## Scraper

Test HTML parsing using representative Hacker News HTML fixtures.

Do not make unit tests depend on the live Hacker News website.

Use fixture HTML files or mocked HTTP responses.

Verify that:
- rank is extracted correctly
- title is extracted correctly
- points are extracted correctly
- comments are extracted correctly
- first 30 stories are handled

## Controller/API

Use WebTestClient.

Test:
- valid filter
- invalid filter
- response structure
- HTTP status
- error handling

## Usage tracking

Test that:
- a request creates a usage record
- timestamp is generated
- filter is stored
- success/error status behaves as expected

Do not require an actual external PostgreSQL instance for basic unit tests.

If integration tests are added, consider Testcontainers with PostgreSQL.

---

# Testing philosophy

Favor a testing pyramid:

Many unit tests
↓
Some integration tests
↓
A small number of API/end-to-end tests

Avoid tests that simply duplicate implementation details.

Tests should verify behavior.

The most important business rules should be extremely well tested:
- word counting
- filtering
- sorting
- usage tracking

---

# Performance considerations

The exercise says performance will be measured.

Do not optimize prematurely, but make reasonable decisions.

Potential approach:

A request:
1. Fetches Hacker News.
2. Parses the first 30 entries.
3. Applies filter in memory.
4. Sorts the resulting list.
5. Stores usage data.

Since only 30 entries are processed, in-memory filtering and sorting is preferable to unnecessary database queries.

The database is primarily being used for usage/audit tracking rather than story storage.

Do NOT persist all scraped stories unless there is a compelling reason.

Document why.

Consider connection pooling and HTTP timeout configuration.

---

# Error handling

Implement centralized error handling using Spring WebFlux mechanisms, such as:

@RestControllerAdvice

Return consistent error responses.

Example:

{
  "timestamp": "...",
  "status": 502,
  "error": "CRAWLING_ERROR",
  "message": "Unable to retrieve Hacker News entries",
  "path": "/api/stories"
}

Do not expose stack traces.

Do not expose internal implementation details.

---

# Code quality

This is an interview exercise.

Prioritize:

- SOLID principles where they make sense
- small classes
- single responsibility
- dependency inversion where useful
- clear naming
- immutable DTOs/models where appropriate
- constructor injection
- no unnecessary repetition
- no magic strings
- no giant service/controller
- no business logic inside controllers
- no static global state
- no unnecessary framework complexity

Use Java features idiomatically.

If the project uses Java 21, take advantage of appropriate modern Java features without making the code unnecessarily clever.

---

# Suggested package structure

Use something close to:

com.example.hackernews

├── domain
│   ├── Story
│   └── StoryFilter
│
├── application
│   ├── StoryService
│   ├── StoryFilterService
│   ├── WordCountService
│   └── UsageTrackingService
│
├── infrastructure
│   ├── crawler
│   │   ├── HackerNewsScraper
│   │   └── ...
│   └── persistence
│       ├── UsageEntity
│       ├── UsageRepository
│       └── ...
│
└── web
    ├── StoryController
    ├── dto
    └── exception

Adapt this to the existing project instead of blindly following it.

---

# Logging

Use structured/meaningful logging.

Log relevant crawler events such as:
- request started
- Hacker News request completed
- number of stories parsed
- filter applied
- request completed
- errors

Do not log sensitive data.

Avoid excessive logging.

---

# README requirements

Create a strong README.

It should explain:

1. Project purpose
2. Architecture
3. Technologies
4. How to run locally
5. Environment variables
6. Database setup
7. API endpoints
8. Example requests/responses
9. Filtering rules
10. Word-counting semantics
11. Testing
12. Design decisions
13. Tradeoffs
14. Potential future improvements

The README should make it possible for a reviewer to clone the repository and run the project without needing to ask questions.

Include curl examples.

---

# Docker

If practical, provide a docker-compose setup for PostgreSQL.

Optionally provide a full local setup.

For example:

docker compose up -d postgres

Then run the Spring Boot application locally.

Do not make Docker unnecessarily complicated.

---

# Important instruction for Claude Code

Before changing code:

1. Inspect the existing repository.
2. Understand the current build system.
3. Check Java version.
4. Check Spring Boot version.
5. Check existing dependencies.
6. Check existing package structure.
7. Check existing tests.
8. Reuse existing conventions where reasonable.

Do not blindly recreate the project.

When implementing a feature, keep the changes scoped.

After each significant change:
- run tests
- run compilation
- inspect failures
- fix them before moving on

At the end:
- run the complete test suite
- verify formatting
- verify no secrets are committed
- verify README instructions
- verify API behavior

Do not add dependencies unless they provide clear value.

---

# Interview-readiness requirement

The resulting code must be understandable enough that I can explain:

1. Why WebFlux was chosen.
2. Why WebClient was chosen.
3. Why Jsoup was chosen.
4. Why stories are filtered in memory.
5. Why PostgreSQL is used for usage tracking.
6. Why R2DBC is used instead of JPA/JDBC.
7. How the word-counting algorithm works.
8. How errors are handled.
9. How the application behaves if Hacker News is unavailable.
10. How the application is tested.
11. What could be improved in a production version.
12. What performance bottlenecks could exist.

Avoid abstractions that I cannot reasonably explain in an interview.

When making architectural decisions, add concise comments/documentation explaining WHY, not WHAT.