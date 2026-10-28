# Releasing 1.0.0

The owner's checklist for the first stable version, once Google grants production access (requested on 2026-10-28; the review takes up to 7 days). How distribution works in general is in [RELEASE.md](RELEASE.md); the Play Console set-up is in [the Play Console guide](play/PLAY-CONSOLE-GUIDE.md). Every step below is what the workflows in `.github/workflows` do today.

**Pushing the `v1.0.0` tag publishes:** it creates the GitHub Release, sends the build to the App Distribution testers and uploads the AAB to the Google Play track in `PLAY_TRACK` (`alpha`). Production only changes when you run *Play promote*.

## 0. Before the tag

- [ ] Google has granted production access (*Dashboard* shows *Production* enabled).
- [ ] The Play service account `owlsplit-ci` has **Release to production** (*Users and permissions → Owlsplit → App permissions*, see the guide's Phase 5). Without it *Play promote* fails.
- [ ] *Production → Countries/regions* are set (the guide starts with Spain).
- [ ] Open Dependabot PRs are merged or closed. In particular `firebase-appcheck` 19 → 20 (#86) is a major update: try it on a device first (App Check is not enforced, but a broken token would show up in Crashlytics), or leave it for 1.0.1.
- [ ] `PLAY_TRACK` is still `alpha`: `gh variable list -R Sebas1705/Owlsplit`.

## 1. Check that main is green

```bash
git checkout main && git pull --ff-only
gh run list -R Sebas1705/Owlsplit --branch main --workflow CI --limit 3
node scripts/release-notes.mjs --check   # 1.0.0 notes and store listing within Play's limits
```

CI skips commits that only touch docs or Markdown, so the latest run may be on an earlier commit; it must be green and no later commit may change code. The iOS job only runs by hand; to build the iOS app once before the release: `gh workflow run ci.yml -R Sebas1705/Owlsplit --ref main`.

## 2. Tag

```bash
git rev-list --count HEAD                # this will be the versionCode of 1.0.0
git tag v1.0.0 && git push origin v1.0.0
```

How the version is derived:

- **versionName** `1.0.0`: `release.yml` takes the tag without the `v` and passes it to `.github/actions/build-release`. No `-suffix`, so the GitHub Release is **not** a pre-release and becomes *Latest*.
- **versionCode**: `git rev-list --count HEAD` at the tagged commit (`build-release`). It is higher than `1.0.0-beta.15` (234) and than every earlier build, so it installs over them and Play accepts it. With this PR squash-merged on top of today's main (241 commits) it is **242**, plus one for every other PR merged before the tag.
- A `main-<n>` build of the same commit (`distribute.yml`) has the same versionCode; testers who already have it are not offered 1.0.0 in the app (same code, nothing to update) and Play never sees `main-<n>` builds.

## 3. What the Release workflow does (`release.yml`)

1. Builds the signed APK and AAB (secrets from Doppler `owlsplit/prd`) and creates the GitHub Release `v1.0.0` with the APK, the AAB and their SHA-256. Its body is GitHub's list of PRs since `v1.0.0-beta.15` (internal ones only); afterwards you can replace it with the [CHANGELOG](../CHANGELOG.md) section: `gh release edit v1.0.0 -R Sebas1705/Owlsplit --notes-file <file>`.
2. Writes the store notes with `scripts/release-notes.mjs`. For 1.0.0 it uses the hand-written texts in `fastlane/release-notes/1.0.0/{en-US,es-ES}.txt` instead of the PR titles.
3. Sends the APK to Firebase App Distribution (group `testers`) with the en-US notes.
4. Copies the notes to `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` and uploads the AAB, the R8 mapping and those notes to the `alpha` track as a completed release.
5. When it succeeds, `deploy-firebase.yml` runs: `owlsplit.web.app/download/latest.json` describes `v1.0.0` (its version, versionCode, size, SHA-256) and `/apk` redirects to its APK.

If it fails after the GitHub Release was created, a re-run stops at "Create the GitHub Release". Fix the cause, delete only the Release (`gh release delete v1.0.0 -R Sebas1705/Owlsplit --yes`, the tag stays) and re-run the run: `gh run rerun <run-id> -R Sebas1705/Owlsplit`.

## 4. Check closed testing

- [ ] *Test and release → Closed testing → Alpha* shows 1.0.0 with the versionCode above and the 1.0.0 notes in both languages. Wait if it is in review.
- [ ] Install it from the Play test link on a phone and go through the main flows (create a group, add an expense, settle up, open the invite link in a browser).
- [ ] *Test and release → Pre-launch report* for that versionCode: no crashes, and no new accessibility or security warnings that need a fix first.

## 5. Promote to production (`play-promote.yml`)

Start with a staged rollout to **20 %** of users:

```bash
gh workflow run play-promote.yml -R Sebas1705/Owlsplit -f from=alpha -f to=production -f rollout=0.2
```

(or *Actions → Play promote → Run workflow* with the same inputs). It copies the Alpha release, notes included, into production as a staged release (`inProgress`, 20 %); nothing is rebuilt or re-uploaded. The first production release goes through Google's review: follow it in *Publishing overview*.

Raise the share when Crashlytics and Android vitals stay clean for two or three days, then finish it:

```bash
gh workflow run play-promote.yml -R Sebas1705/Owlsplit -f from=production -f to=production -f rollout=0.5
gh workflow run play-promote.yml -R Sebas1705/Owlsplit -f from=production -f to=production -f rollout=1
```

With `from` = `to` it only changes the share of the release in progress; `1` completes it.

## 6. After the release

- [ ] **Crashlytics** (Firebase console, project `owlsplit`): filter by version `1.0.0`; crash-free users at least as good as `1.0.0-beta.15`, no new issues.
- [ ] **Play Console → Android vitals**: ANR and crash rates below the bad-behaviour thresholds.
- [ ] **latest.json**: `curl -s https://owlsplit.web.app/download/latest.json` shows `"version": "1.0.0"`, `"tag": "v1.0.0"` and the versionCode above.
- [ ] **Download page**: [owlsplit.web.app](https://owlsplit.web.app) says *Versión 1.0.0* / *Version 1.0.0*, and `curl -sI https://owlsplit.web.app/apk` redirects to `Owlsplit-v1.0.0.apk`.
- [ ] **In-app notice**: a phone with a beta installed from the APK shows the update card on Home (the app compares versionCodes; Play installs use Google Play in-app updates instead).
- [ ] **GitHub**: `v1.0.0` is *Latest* and not a pre-release.
- [ ] A docs PR: the 1.0.0 date in `CHANGELOG.md`, and the README's "public beta" status and "Google Play closed testing" download line updated to the Play Store listing.

## 7. Rollback

Google Play cannot go back to a lower versionCode, so a bad version is stopped and then **fixed forward**:

1. **Stop the rollout** in Play Console: *Production → Releases → Manage release → Halt rollout*. Do it in the console: *Play promote* cannot halt (it only changes the share of a release in progress). Users who already updated keep 1.0.0.
2. **Fix forward**: merge the fix, tag `v1.0.1` (a higher versionCode), check it in Alpha and promote it as in step 5. It replaces the halted release.
3. **APK and web**: `latest.json` and `/apk` always point at the newest GitHub Release, so they move to 1.0.1 when it is out. Devices never get told to go back (the app only offers a higher versionCode).
4. Firestore rules and indexes do not change with a tag; if a fix needs them, they deploy from `main` (`deploy-firebase.yml`).
