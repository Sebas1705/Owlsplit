# Distribution

Everything is distributed through **Firebase App Distribution** (project `owlsplit`, group `testers`), always the same app: `es.sebas1705.owlsplit`, signed with the release key. There are two moments:

| When | What testers get | Workflow |
|---|---|---|
| Every merge to `main` that changes the app | Build `main-<n>` | `.github/workflows/distribute.yml` |
| A `vX.Y.Z` tag | Version `X.Y.Z`, plus a GitHub Release with APK, AAB, SHA-256 and notes | `.github/workflows/release.yml` |

`versionCode` is the number of commits on `main`: it always grows, so each build installs over the previous one, whether it comes from `main` or from a tag.

Testers get an App Distribution email with the download link (the first time they accept the invitation and install the APK from the browser or with the *Firebase App Tester* app). Adding testers: `node scripts/setup-ci-access.mjs correo@…` (it also rotates the CI key) or the App Distribution console.

Debug builds (`es.sebas1705.owlsplit.debug`, `./gradlew :composeApp:installDebug`) are for local development only: they install alongside the testers' build and use the same project.

## Publishing a version

```bash
git tag v1.0.0 && git push origin v1.0.0
```

It can also be triggered manually from Actions with the version (`X.Y.Z-beta.1` creates a prerelease). The AAB goes to Google Play's `PLAY_TRACK` channel (see [the Play Console guide](play/PLAY-CONSOLE-GUIDE.md)).

**Release notes** are written once: GitHub generates them from the merged PRs, and `scripts/release-notes.mjs` turns the user-facing ones (`feat`/`fix`/`perf` titles, prefix removed; `docs`/`ci`/`chore`/… left out) into the short en-US and es-ES notes that App Distribution and Google Play show (500 characters each). So **PR titles are the changelog**: write them for users.

When the Release finishes, `deploy-firebase.yml` redeploys Hosting with that version's APK. That way **[owlsplit.web.app](https://owlsplit.web.app)** always offers the latest download, betas included:

- `owlsplit.web.app/apk` redirects to the APK of the latest GitHub Release (the repo is public, so it downloads without an account);
- `/download/latest.json` gives the version, link, size, SHA-256 and date.

The APK is not uploaded to Hosting because the Spark plan forbids executables.

All secrets come from Doppler `owlsplit/prd`; GitHub only stores `DOPPLER_TOKEN`, a read-only token.

## Setup (done on 2026-10-08)

- Project `owlsplit`: Firestore `eur3`, Android (release and debug) and iOS apps, rules, indexes and Hosting.
- Firebase configuration in Doppler (`scripts/setup-firebase-env.sh`).
- Release key `Documents\owlsplit-release.jks` in Doppler (`scripts/setup-release-keystore.sh`); SHA-1 `D9:45:BC:07:…:08:C3`, SHA-256 `99:0B:AD:25:…:18:C3`, registered in the release app and in `assetlinks.json`. **Keep a copy of the `.jks` off the PC**: it cannot be replaced.
- Service account `owlsplit-ci@owlsplit`, its key in Doppler, `DOPPLER_TOKEN` in GitHub and the `testers` group (`scripts/setup-ci-access.mjs`).
- Authentication enabled (Anonymous and Google) since 2026-10-08; `google-services.json` includes the Google web client.
