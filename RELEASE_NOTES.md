# NekoBox 1.4.3 (fork build)

- Add a two-column node-card layout with compact per-node actions.
- Add subscription name filters: keep matching names and remove matching names (case-insensitive regular expressions).
- Add HTTPS WebDAV backup and restore with AES-GCM encryption using a user-entered cross-device passphrase. The passphrase is not stored by the app. Older WebDAV JSON backups remain importable; local NekoBox v1 JSON import remains supported.
- A backup passphrase is required to restore encrypted WebDAV backups; unrecoverable if forgotten. Existing remote JSON backups may contain plaintext secrets.

This fork uses package ID `moe.nb4a`; the official NekoBox app has the same ID. Install as an in-place update only when signed with the same signing certificate as the installed app. Do not use a debug-signed APK to upgrade an official install.
