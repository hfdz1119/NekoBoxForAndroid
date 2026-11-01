# JayBox release signing setup

JayBox's standalone application ID is `moe.nb4a.fork`. For release updates to install over an existing JayBox build, keep using the same private signing keystore and alias; losing that key prevents ordinary in-place updates.

Create a new persistent key on a trusted computer (do not commit the keystore):

```sh
keytool -genkeypair -v -keystore jaybox-release.jks -alias jaybox -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and both passwords in a secure password manager or encrypted backup. Do not send them through chat or commit them to the repository. Base64-encode the keystore file for GitHub Actions (`base64 -w 0 jaybox-release.jks` on GNU/Linux; on macOS use `base64 < jaybox-release.jks | tr -d '\n'`). Add these repository Actions secrets under Settings → Secrets and variables → Actions:

- `RELEASE_KEYSTORE_BASE64`: base64 text of the `.jks` file, without wrapping newlines.
- `KEYSTORE_PASS`: keystore password.
- `ALIAS_NAME`: the key alias (for the example above, `jaybox`).
- `ALIAS_PASS`: key password.

The release workflow decodes the keystore into the runner's temporary directory, uses it only for the release build, and removes it when the build step exits. It fails before building if any required secret is missing. No signing key or secret is included in this repository.
