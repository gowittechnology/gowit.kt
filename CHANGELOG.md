# Changelog

All notable changes to the Gowit Kotlin SDK are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-03

### Added
- `AdContext`: one object for the request context — `customer`, `language`, `locationId`,
  `regionId`, `search`, `category`, `categoryId`, `products`, `filters`, `pageNumber`, `sessionId`.
- App-wide default context: `Gowit.shared.setDefaultContext(...)` / `Gowit.shared.defaultContext`.
  Applied to every ad request and every `SponsoredDisplayView`. More specific values win;
  `customer` is merged field by field; lists are replaced.
- `Gowit.shared.getAds(placement, adContext, sessionId)` for single-placement requests with context.
- `SponsoredDisplayView.setup(placement, adContext, sessionId, onClick, onAdClick, callbacks)`
  (suspend and lifecycle-aware variants). The view now sends the full request context.
- Click interception: `onAdClick: (ad, destinationUrl) -> Boolean`. Return `true` to handle
  navigation in the app; `false` keeps the default behavior (open the URL with `ACTION_VIEW`).
  The click event is recorded in both cases.
- `AdRequestBuilder.withLanguage(...)` and `AdRequestBuilder.with(context)`.
- `Product.categories` and `Product.categoryIdStructures` (also on `ProductRequest`) for
  products that belong to several categories.
- Unit tests for context merging and the request body sent to `/server/v2/sdk/ads`.

### Changed
- `SponsoredDisplayView` uses the session ID passed to `setup` (or the context/default session)
  for impression and click events. Previously events used an internal random ID.
- `SponsoredDisplayView` no longer forces `page_number = 1`; it sends the context's `pageNumber`.
- `SponsoredDisplayView` picks the ad of its own placement from the response instead of the
  first placement returned.
- `SponsoredDisplayView.refresh()` now requests a new ad with the same placement, context and session.
  Previously it only reset state.
- `getAds(placements, pageNumber, sessionId)` and `getAds(builder)` apply the default context.

### Fixed
- `language` set on `AdRequestBuilder` is now sent with the request.
- The deprecated `setup(sponsoredDisplayConfig, customerId, ...)` now sends `customerId` and
  `CustomPlacement.locationId`; both were previously dropped.

### Deprecated
- `SponsoredDisplayView.setup(sponsoredDisplayConfig, ...)`. Use `setup(placement, adContext, ...)`.
  Existing calls keep working unchanged.

## [1.0.5] - 2026-07-01
### Changed
- SDK impression and click events send `no_redirect=1`.

## [1.0.4] - 2026-07-01
### Fixed
- Corrected the SDK ad and event endpoints.

## [1.0.3] - 2026-05-08
### Fixed
- Corrected the event reporting methods (SUP-1603).

## [1.0.0] - 2025-09-08
### Added
- Initial release: configuration, ad requests with placements and filters, `SponsoredDisplayView`,
  impression, click and sale events.

[1.1.0]: https://github.com/gowittechnology/gowit.kt/compare/1.0.5...1.1.0
[1.0.5]: https://github.com/gowittechnology/gowit.kt/compare/1.0.4...1.0.5
[1.0.4]: https://github.com/gowittechnology/gowit.kt/compare/1.0.3...1.0.4
[1.0.3]: https://github.com/gowittechnology/gowit.kt/compare/1.0.0...1.0.3
[1.0.0]: https://github.com/gowittechnology/gowit.kt/releases/tag/1.0.0
