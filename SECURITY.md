# Security Policy

## Supported versions

Only the latest release (Google Play and the download page) gets fixes.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting: **Security tab -> Report a vulnerability**
([direct link](https://github.com/Sebas1705/Owlsplit/security/advisories/new)). Please do not open a
public issue for a vulnerability.

## What the app does today

This describes the code as it is; see [docs/FIREBASE.md](docs/FIREBASE.md) for the details.

- **Accounts**: Firebase Authentication (anonymous, Google, Apple). There is no password and no Owlsplit server of
  our own.
- **Data**: groups, members, expenses and payments live in Cloud Firestore. Each group is readable and writable only
  by the accounts listed in its `memberUids`; the access rules are in `firebase/firestore.rules` and are tested
  against the Firestore emulator (`firebase/rules-test`). Joining needs the group's invite code, and an invite
  document can be read only by someone who already knows its code.
- **Money**: amounts are integers in minor units. The rules check types and ranges of every field, but they cannot
  check that the payers or shares of an expense add up to its amount; the clients check that, and leave out an entry
  that does not add up.
- **Guest web** (`/g/<code>`): the same anonymous sign-in and rules as the apps, served from Firebase Hosting with a
  Content Security Policy and the usual hardening headers (`firebase/firebase.json`).
- **App Check** is sent by the apps and the guest web, but it is **not enforced** yet, so it is not a protection today.
- **On the device**: a local SQLite database (SQLDelight) in the app's private storage, not encrypted by the app
  itself. Android backup and device-to-device transfer are turned off. The app stores no password and no API token;
  the `SecureStorage` class in `core/security` exists but nothing uses it.
- **Network**: every endpoint the app calls (Firebase, the exchange-rate API, the invite and download pages) is
  `https`. The app does not add its own certificate pinning or cleartext check; it relies on the platform defaults.
- **Reports and crashes**: release builds send crashes to Crashlytics. "Report a problem" sends what the user writes
  only when they tap Send.
