# JayBox 1.4.3

- Add a two-column node-card layout with compact per-node actions.
- Add subscription name filters: keep matching names and remove matching names (case-insensitive regular expressions).
- Add HTTPS WebDAV backup and restore with AES-GCM encryption using a user-entered cross-device passphrase. The passphrase is not stored by the app. Older WebDAV JSON backups remain importable; local NekoBox v1 JSON import remains supported.
- A backup passphrase is required to restore encrypted WebDAV backups; unrecoverable if forgotten. Existing remote JSON backups may contain plaintext secrets.

JayBox is a standalone fork using package ID `moe.nb4a.fork`; it can be installed alongside the official NekoBox app. The launcher icon is unchanged.
