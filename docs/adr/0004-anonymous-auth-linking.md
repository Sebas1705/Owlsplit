# 0004 · Linkable anonymous account and ghost members

- **Status:** accepted · 2026-10-07

## Context
Sign-up friction is the biggest obstacle to a group adopting an expenses app. Also, there is always someone in the group who does not want to install anything.

## Decision
- Each install starts with **anonymous Firebase Auth**, with no sign-up screen. From Profile, Google or Apple is **linked** (`linkWithCredential`), keeping the uid and data.
- **Members** are group entities, independent of users: they can be **ghosts** (name only) and a user **claims** one when joining by invite.
- Account deletion turns its members into ghosts, so the group's accounting does not change.

## Consequences
- Risk of losing the anonymous account if the app is uninstalled without linking: mitigated with prompts to link at key moments.
- The rules must distinguish uid (access) from member (accounting).
- There is a flow to sign in to an existing account bringing along the anonymous account's groups ([FIREBASE.md](../FIREBASE.md#authentication)).

## Rejected alternatives
- **Mandatory sign-up**: simpler to secure, but more friction.
- **Only members with an account**: unworkable for a dinner with someone who does not have the app.
