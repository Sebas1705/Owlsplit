# Changelog

What changes for Owlsplit users in each version. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/). Pre-releases (`X.Y.Z-beta.N`) are only listed in [GitHub Releases](https://github.com/Sebas1705/Owlsplit/releases); each stable version here sums them up. Numbers in brackets are pull requests.

## [Unreleased]

## [1.0.0] - Unreleased

The first stable version, after fifteen pre-releases (`1.0.0-beta.1` to `1.0.0-beta.15`, 8–16 October 2026) tested through Firebase App Distribution and Google Play closed testing. Android and iOS, in Spanish and English.

### Groups & expenses

- Groups with members, expenses and balances (#15).
- Expenses in several currencies, with several payers, a date, and manual payments (#16).
- Recurring expenses (#32).
- New expenses follow the group's member weights (#65).
- Type an expense as a sentence to fill in the form (#68).
- History, activity, filters and conflicts (#17); people who left are no longer shown as "Someone" in the history (#27).
- Invite with a code, a link or a QR code, join a group and claim a member (#18, #19), with Android App Links (#19).
- Leave a group and go back into it with your member (#43, #66).
- A Stats tab with your spending across groups and a summary card in each group (#59), with charts: a category donut, monthly columns and bars by group and person (#60).
- Home-screen balance widget and an add-expense shortcut (#64).

### Sync & offline

- A change is uploaded once even when the sync loop restarts mid-upload; before, some history entries were sent twice and rejected by the server (#91).
- Local-first: everything is saved on the phone (#9) and synced through Firestore (#12, #13).
- A write that times out but reaches the server is no longer reported as a conflict (#47).
- The UI updates after linking or unlinking an account, with nothing left to sync (#52).
- Fewer Firestore reads (#57).

### Settle up

- "Where does this come from?": a sheet on Balances and Settle up that shows how each balance adds up (paid, share, payments sent and received) and why settling needs so few payments (#90).
- Settle up in one tap with Bizum, PayPal or Revolut (#29).
- Pay by IBAN with a SEPA QR to scan from the banking app (#63).
- Open your bank for Bizum after copying the phone number (#79).
- Remind someone who owes you, from Settle up (#56).
- When you come back from the payment app, Owlsplit asks whether you paid (#67).

### Export & import

- Group summary and export to CSV and PDF (#30).
- Import a group from Splitwise (#31).

### Web for guests

- Open the group in the browser from the invite link, without installing the app (#70).
- Edit and delete expenses, and undo recorded payments, from the web (#72).
- Expenses in other currencies at the ECB rate, keeping what was typed (#73).
- Save the guest with Google so the account goes on in the app (#74).
- Protected by Firebase App Check with reCAPTCHA Enterprise (#76, #77).
- Download page at [owlsplit.web.app](https://owlsplit.web.app) with the APK of the latest version (#33, #34, #36).

### Accounts

- Start without signing up, and link a Google account (#14, #25).
- Onboarding again after deleting the account (#25).
- Linking a Google account that already exists signs into it and brings its groups (#48).
- One Google account at a time, with Unlink, and claim any member that has no account (#49).
- When linking Google, the cloud profile wins (#50); relinking takes a single Google sign-in and keeps the cloud profile (#61).
- Android backups and phone-to-phone transfers no longer copy the session, encrypted settings or local database, which another phone cannot decrypt or would restore stale; sign in with Google to get your groups back. The error screen's Retry button is translated.

### Also in 1.0.0

- Spanish and English, with a language setting, and an accessibility pass (#21).
- iOS app with share sheet, QR scanner, reduced motion and links (#23).
- Update notice on Home (#44, #53) and Google Play in-app updates for Play installs (#55).
- Crash reports with Crashlytics (#22).
- New design system, brand and app icon (#10, #26, #37, #38).
- Open source under the MIT licence (#35).

### Since 1.0.0-beta.15

- No user-facing changes: dependency updates (#39, #40, #41), code coverage in CI (#81) and project docs (#87, #88).

[Unreleased]: https://github.com/Sebas1705/Owlsplit/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Sebas1705/Owlsplit/compare/v1.0.0-beta.15...v1.0.0
