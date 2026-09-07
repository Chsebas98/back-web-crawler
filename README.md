# Hacker News Crawler

A reactive Spring WebFlux backend that scrapes the first 30 stories from
[Hacker News](https://news.ycombinator.com/), filters/sorts them by two
exercise-defined rules, and records usage/audit data for every request. Built
as a take-home/interview exercise, so the code favors clarity and
explainability over unnecessary abstraction.

## 1. Project purpose

Expose a small REST API that:

1. Scrapes the first 30 Hacker News front-page entries (rank, title, points, comments).
2. Filters and sorts them by one of two rules, selected via a query parameter.
3. Records who asked for what, when, and whether it succeeded, in PostgreSQL.

A separate React + TypeScript + Vite frontend (not in this repo) consumes this API.

## 2. Architecture

```
com.web.crawler.web_crawler
├── domain              # Story, StoryFilter, UsageStatus - plain, immutable models
├── application          # WordCountService, StoryFilterService, StoryService, UsageTrackingService
├── infrastructure
│   ├── crawler          # HackerNewsClient (HTTP), HackerNewsScraper (HTML -> Story), config, exceptions
│   └── persistence       # UsageEntity (R2DBC row), UsageRepository
└── web                  # StoryController, CORS, security, DTOs, centralized error handling
```

Request flow for `GET /api/stories?filter=...`:

```
StoryController
  -> parses & validates the filter (400 if invalid, no silent fallback)
  -> StoryService
       -> HackerNewsClient.fetchHomepageHtml()   (WebClient, non-blocking)
       -> HackerNewsScraper.scrapeTopStories()   (Jsoup HTML -> List<Story>)
       -> StoryFilterService.apply(...)          (in-memory filter + sort)
       -> UsageTrackingService.recordSuccess/Failure(...)  (R2DBC, non-blocking)
  -> any failure (thrown here or anywhere else, including an unmapped route)
     is caught by GlobalErrorWebExceptionHandler, which renders it in the
     same ApiResponse envelope as a success
```

Each stage only knows about the layer below it through a narrow contract
(`Story`, not raw HTML or DOM nodes; a `StoryFilter` enum, not strings), so
Hacker News changing its markup, or the filtering rules changing, stays
isolated to one class.

## 3. Technologies

- Java 21, Spring Boot 4 (WebFlux, reactive end-to-end)
- Spring WebClient for the outbound HTTP call (non-blocking, unlike `RestTemplate`)
- Jsoup for HTML parsing (simple CSS-selector API, no full browser/NLP dependency)
- PostgreSQL + Spring Data R2DBC for usage/audit persistence (non-blocking)
- Flyway for schema migrations (JDBC, startup-only - see [Design decisions](#12-design-decisions))
- JUnit 5, Reactor Test (`StepVerifier`), `WebTestClient`, Mockito, AssertJ
- Maven

## 4. How to run locally

Everything runs locally - no Docker.

1. Make sure PostgreSQL is running locally and the `crawler_db` database exists:
   ```bash
   createdb crawler_db
   ```
2. Copy `.env.example` to `.env` (or just export the variables) and adjust credentials if needed.
3. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway creates the `crawler_usage` table automatically on startup.
4. The API is available at `http://localhost:8081`.

## 5. Environment variables

All configuration lives in `application.properties`, externalized via env vars (see `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | PostgreSQL credentials |
| `HACKERNEWS_BASE_URL` | `https://news.ycombinator.com/` | Scraper target |
| `HACKERNEWS_TIMEOUT` | `5s` | WebClient connect/response timeout |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed frontend origins (never wildcarded) |

`server.port` is fixed to `8081` in `application.properties`.

## 6. Database setup

PostgreSQL is used **only** for usage/audit tracking, not for storing scraped
stories (see [Design decisions](#12-design-decisions)). The schema is a single
table, created by the Flyway migration at
`src/main/resources/db/migration/V1__database.sql`:

```sql
CREATE TABLE IF NOT EXISTS crawler_usage (
    id                 BIGSERIAL PRIMARY KEY,
    request_timestamp  TIMESTAMPTZ NOT NULL,
    filter             VARCHAR(50) NOT NULL,
    execution_time_ms  BIGINT,
    stories_scraped    INTEGER,
    stories_returned   INTEGER,
    status             VARCHAR(20) NOT NULL,
    error_message      TEXT
);
```

Flyway runs this automatically the first time the app starts against a fresh
database; no manual step is needed beyond creating the empty `crawler_db` database.

## 7. API endpoints

### `GET /api/stories?filter={filter}`

| `filter` value | Rule | Sort order |
|---|---|---|
| `MORE_THAN_FIVE_WORDS` | Title has more than 5 words | `comments` descending |
| `FIVE_OR_FEWER_WORDS` | Title has 5 or fewer words | `points` descending |

Any other (or missing) value returns `400 Bad Request` - there is no default fallback.

### Response envelope

**Every** response from this API - success or failure, including framework-level
errors like an unmapped route (404) or an unsupported method (405) - uses the
same shape, so the frontend never has to branch its parsing logic on the HTTP
status code:

```ts
{
  response: boolean;      // true on success, false on any failure
  statusCode: number;     // the HTTP status, repeated in the body for convenience
  message: string;        // short, front-end-facing title, meant for an alert/toast
  result: T | null;       // the payload (object or array) on success, always null on failure
  errorDetail: string | null; // safe, generic description of the failure; null on success
}
```

`errorDetail` is always a generic, safe-to-display string - it never echoes the
underlying exception message, which could leak internal implementation
details. The real error is still logged server-side by
`GlobalErrorWebExceptionHandler` and, for scraping failures, recorded in
`crawler_usage.error_message` for debugging - just never returned to the caller.

## 8. Example requests/responses

```bash
curl "http://localhost:8081/api/stories?filter=MORE_THAN_FIVE_WORDS"
```
```json
{
  "response": true,
  "statusCode": 200,
  "message": "Stories retrieved",
  "result": [
    { "number": 10, "title": "Discovery of a new OpenAI agent message board", "points": 2141, "comments": 1527 },
    { "number": 14, "title": "Actively exploited sandbox RCE in all Chromium versions", "points": 754, "comments": 447 }
  ],
  "errorDetail": null
}
```

Invalid filter:
```bash
curl "http://localhost:8081/api/stories?filter=NOT_A_FILTER"
```
```json
{
  "response": false,
  "statusCode": 400,
  "message": "Information incomplete",
  "result": null,
  "errorDetail": "The information provided is incomplete or invalid. Please try again."
}
```

Hacker News unreachable/malformed (`502`), an unmapped route (`404`), and an
unsupported HTTP method (`405`) all follow the exact same shape, only
`statusCode`/`message`/`errorDetail` change - see
[`GlobalErrorWebExceptionHandler`](src/main/java/com/web/crawler/web_crawler/web/exception/GlobalErrorWebExceptionHandler.java)
for the full mapping.

## 9. Filtering rules

- **`MORE_THAN_FIVE_WORDS`**: keep stories whose title has more than 5 words, sort by `comments` descending.
- **`FIVE_OR_FEWER_WORDS`**: keep stories whose title has 5 or fewer words (exactly 5 included), sort by `points` descending.
- Filtering and sorting happen **in memory** (`StoryFilterService`), not via a database query: only 30 stories are ever involved per request, so an in-memory pass is simpler and cheaper than a round trip to PostgreSQL - which, by design, only stores usage/audit data.
- Ties are broken by preserving the original scrape order (`Stream.sorted` is a stable sort).

## 10. Word-counting semantics

Implemented in `WordCountService`, the only place this rule lives:

- Split on whitespace only - never on punctuation.
- A token counts as a word only if it contains at least one letter or digit.
- A token made entirely of punctuation/symbols (`-`, `---`, `!`) does not count.
- A hyphen attached to letters (`self-explained`) stays a single word, since splitting never happens on `-`.

Example: `"This is - a self-explained example"` → 5 words (`This`, `is`, `a`,
`self-explained`, `example`); the lone `-` is discarded.

## 11. Testing

```bash
./mvnw test
```

Covers, per the testing pyramid (many unit tests, few integration/API tests):

- **`WordCountServiceTest`** - every case from the exercise: normal titles, punctuation, standalone symbols, hyphens, multiple spaces, leading/trailing spaces, empty/null/whitespace-only titles.
- **`StoryFilterServiceTest`** - filtering, the exact-5-word boundary on both filters, sorting, and tie behavior, using deterministic hand-built `Story` data.
- **`HackerNewsScraperTest`** / **`HackerNewsClientTest`** - HTML parsing against static fixtures under `src/test/resources/fixtures` (30 stories, a job-posting-like row, a zero-comments row, malformed/insufficient-story fixtures) and a stubbed `ExchangeFunction`. No network access, no dependency on the live site.
- **`UsageTrackingServiceTest`** - a request creates a usage record, the timestamp is backend-generated, filter/status are stored correctly on both success and failure, and a persistence failure never propagates. Uses a mocked repository - no real PostgreSQL needed.
- **`StoryServiceTest`** - the orchestration: scraped stories are filtered and usage is recorded on success; on scraper failure, a `status=ERROR` usage record is still written and the original error still propagates to the caller.
- **`StoryControllerTest`** - `WebTestClient` against the real controller with a mocked `StoryService`: valid filter, invalid filter, missing filter, response shape, and error-to-status-code mapping.

## 12. Design decisions

- **WebFlux + WebClient, not `RestTemplate`**: the whole stack is reactive; a blocking HTTP client would tie up event-loop threads under load.
- **Jsoup over a heavier parser/tokenizer**: a CSS-selector DOM API is enough for Hacker News's markup and keeps parsing code readable - no NLP library is needed for the word-counting rule either.
- **In-memory filtering/sorting**: only 30 entries are ever processed per request, so a database round trip would add latency for no benefit.
- **PostgreSQL only for usage tracking, never for scraped stories**: stories are always re-scraped live; persisting them would add staleness and complexity with no requirement driving it.
- **R2DBC, not JPA/JDBC, at runtime**: keeps the whole request path non-blocking. Flyway itself uses a JDBC connection, but only at startup to run migrations - Flyway has no R2DBC support, and this is a one-time, non-request-path operation, so it doesn't compromise the reactive runtime.
- **Two scraper exceptions, not one**: `HackerNewsUnavailableException` (network/HTTP failure) vs. `HackerNewsParsingException` (malformed HTML/missing elements/fewer than 30 stories). Both map to a `502` failure envelope for the caller, but keeping them distinct in code separates "retry might help" from "the scraper needs a code fix."
- **One global `ErrorWebExceptionHandler`, no `@RestControllerAdvice`**: a `@RestControllerAdvice`'s `@ExceptionHandler` methods only intercept exceptions from a *matched* controller method - a 404 for a route that doesn't exist never reaches one, since no controller was matched. `GlobalErrorWebExceptionHandler` extends `AbstractErrorWebExceptionHandler` (the same extension point Spring Boot's own default error handler uses) and is registered at Boot's default precedence, so it is the single funnel for every failure - business exceptions, invalid/missing request parameters, unmapped routes, unsupported methods - guaranteeing the same `ApiResponse` envelope everywhere with no risk of a code path bypassing it.
- **`errorDetail` is always generic**: the real exception message is logged server-side (and, for scraping failures, still stored in `crawler_usage.error_message` for audit/debugging) but never sent to the caller, so a response can never leak internal implementation details.
- **Job postings default to 0 points/comments** rather than failing: Hacker News legitimately omits both for job listings, so treating that as corrupted data would be wrong. A missing *title*, by contrast, still fails loudly (`HackerNewsParsingException`).
- **Usage tracking failures never break the API response**: recording usage is an audit side-effect, not part of the crawler's contract with its caller. A `UsageTrackingService` persistence error is logged and swallowed; a *scraping* failure is still recorded (`status=ERROR`) and then re-thrown so the caller gets a proper `502`.
- **No response DTO for `Story`**: `Story` is a plain immutable domain record already shaped exactly like the required JSON, so returning it directly avoids a mapping layer with no benefit. This is different from `UsageEntity` (a persistence entity), which is never exposed through the API.
- **Security is intentionally opened up**: `spring-boot-starter-security` is on the classpath (from the initial project setup) and would otherwise lock every endpoint behind a generated login. Since this exercise has no auth requirement, `SecurityConfig` explicitly permits all requests - documented as a deliberate choice, not an oversight, and a clear place to add real authentication later.
- **Filter as an enum, not a string**: `StoryFilter` is validated once at the controller boundary; an unmatched value is a `400`, never a silent fallback.

## 13. Tradeoffs

- No caching of Hacker News responses: every request re-scrapes live. Simpler and always fresh, at the cost of latency and load on Hacker News under heavy traffic.
- No pagination/history endpoint over `crawler_usage`: the table is written but never read back by the API, since the exercise only asks for tracking, not reporting.
- No integration test against a real/Testcontainers PostgreSQL: unit tests with a mocked repository cover the required behavior without adding a Testcontainers dependency; a real integration test would strengthen confidence in the Flyway migration + R2DBC mapping together.
- Rank is trusted from Hacker News' own numbering (parsed from the page) rather than recomputed from array position - if Hacker News' numbering were ever inconsistent, this would surface as scraped data rather than being silently corrected.

## 14. Potential future improvements

- Cache the scraped homepage for a short TTL to reduce load on Hacker News and improve latency for concurrent requests.
- Add an endpoint to query `crawler_usage` (e.g., recent requests, error rate) for observability.
- Add Testcontainers-based integration tests covering the Flyway migration and R2DBC repository together.
- Add rate limiting if this were exposed publicly rather than to a single trusted frontend.
- Replace the generated-per-request timing with Micrometer metrics for proper observability (histograms, dashboards) instead of a single per-request column.
