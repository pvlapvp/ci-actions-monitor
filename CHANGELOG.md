# Changelog

All notable changes to this plugin are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.1.0] - 2026-11-08

### Added

- Provider abstraction (`ActionsProvider`) with Gitea and GitHub implementations.
- Multiple connections; every connection has its own provider type, server URL,
  optional API URL override, timeout and repository list.
- GitHub.com and GitHub Enterprise Server support in read-only mode, including
  fine-grained PAT with **Actions: Read-only**, **Metadata: Read-only** and
  **Contents: No access**.
- Test connection button in the connection dialog with server version and account
  name, or a readable error.
- Statuses `Neutral`, `Action required`, `Stale` and `Unknown` in addition to the
  existing queued/running/completed states.
- Rate-limit backoff for automatic run/job tree refresh and log polling
  (`Retry-After` and `X-RateLimit-Reset`).
- Streaming job log download with cancellation, a 10 MB cap and explicit
  truncation reporting.
- Manual log reload and automatic log refresh for running jobs.
- `ProviderCapabilities` so page sizes follow each provider's limits.

### Changed

- Plugin renamed from **Gitea Actions Monitor** to **CI Actions Monitor**.
- Package renamed from `com.company.gitea` to `ru.pvlapvp.ciactionsmonitor`.
- The Services root node is now **CI Actions**; the open action is **Open in Browser**.
- Gitea DTOs moved to `provider/gitea/dto` and separated from the canonical model.
- Settings now store connections (id, provider type, name, URLs, timeout,
  repositories) instead of a single server.
- Auto-refresh log messages are provider-neutral.

### Security

- Access tokens are stored in IntelliJ PasswordSafe and are bound to a stable
  connection id.
- Legacy plaintext tokens from the 1.0 XML are removed immediately and migrated to
  PasswordSafe asynchronously.
- GitHub job-log redirects are followed manually; the `Authorization` header is
  never sent to the signed storage host.

### Known limitations

- Read-only: workflows cannot be started, cancelled or re-run.
- GitHub Enterprise Cloud (`*.ghe.com`) is not supported yet.
- No conditional requests (`ETag` / `If-None-Match` / `304`) yet.
- Automatic title refresh covers only expanded workflows and runs.

## [1.0.0]

- Initial release: Gitea Actions in the Services tool window.
