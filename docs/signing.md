# Release signing

The tag-triggered GitHub Actions release workflow signs the APK using a private Android keystore. Create the keystore locally and keep a secure backup; never commit it to the repository. In PowerShell, run:

```powershell
keytool -genkeypair -v -keystore tubearchivist-share-release.jks -storetype JKS -alias tubearchivist-share -keyalg RSA -keysize 2048 -validity 10000
```

Choose a strong keystore password when prompted. At the key-password prompt, press Enter to use the same password. Use alias `tubearchivist-share` and add these repository secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: the keystore encoded as Base64. In PowerShell, copy the encoded value to your clipboard with:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes(".\tubearchivist-share-release.jks")) | Set-Clipboard
  ```
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: `tubearchivist-share` (or the alias you chose).
- `ANDROID_KEY_PASSWORD`: the key password.

After these secrets are set, pushing a version tag builds and signs the release APK. The signing key and passwords are not stored in the repository; local builds remain unsigned unless the corresponding `ANDROID_*` environment variables are provided.
