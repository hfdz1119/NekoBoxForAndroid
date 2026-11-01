# JayBox signing and build

JayBox uses application ID `top.hfdz1119.jaybox`, namespace `io.nekohasekai.sagernet`, and the existing NekoBox launcher icon. The package ID allows it to coexist with the official NekoBox app. Keep one signing key permanently: future JayBox releases need the same key to update an existing installation.

> GitHub requires a `workflow_dispatch` workflow file to exist on the repository default branch. These files are committed only on `jaybox` as requested, so make the workflow definitions available on the default branch (for example, by reviewing and merging them) before they can be launched; then choose `jaybox` as the run branch. This change does not alter the default branch.

## Generate the long-term key

1. In the repository's **Settings → Secrets and variables → Actions**, add `JAYBOX_KEYSTORE_PASSWORD` with a strong password. The key-generation workflow uses it as both the JKS store password and the `jaybox` alias password; it is never stored in the repository.
2. Open **Actions → Generate JayBox signing key → Run workflow** and run it on the `jaybox` branch. The workflow is manual-only, refuses to overwrite an existing file in its runner workspace, and uploads `jaybox-release.jks` as the `jaybox-signing-key` artifact for seven days.
3. Download and extract that artifact promptly. Store the JKS and password in a secure password manager or encrypted backup. The Actions artifact is temporary and is not a long-term key backup. Never commit the JKS or send it through chat.

## Configure signed APK builds

In **Settings → Secrets and variables → Actions**, add these four repository secrets:

- `KEYSTORE_BASE64`: base64 encoding of the downloaded `jaybox-release.jks` file. Linux: `base64 -w 0 jaybox-release.jks`; macOS: `base64 < jaybox-release.jks | tr -d '\n'`.
- `KEYSTORE_PASSWORD`: the password used when generating the JKS.
- `KEY_ALIAS`: `jaybox`.
- `KEY_PASSWORD`: the alias password used when generating the JKS (the same as `KEYSTORE_PASSWORD` for this workflow-generated key).

Then open **Actions → Build signed JayBox APKs → Run workflow**, select branch `jaybox`, and run it. The workflow checks the provided credentials, builds the existing OSS release variants, verifies each APK signature and application ID, deletes the temporary JKS from the runner, and uploads the APKs as the `jaybox-1.4.3-apks` artifact for 30 days. Download the APK artifact from that workflow run. It does not publish a GitHub Release or change the repository's default branch.

Ordinary Debug builds retain Android's normal debug certificate and do not require these secrets. Release APK/AAB packaging fails clearly unless a keystore path, store password, alias, and key password are all supplied. The workflow uses a temporary runner keystore and does not use the repository's existing legacy `release.keystore` file.

If the signing key is lost or replaced, Android will not allow that build to update installs signed with the original key. Keep the JKS and its password backed up independently of GitHub.
