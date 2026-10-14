# 0003 · SQLDelight as source of truth + custom sync

- **Status:** accepted · 2026-10-07

## Context
The app is used on trips, often without coverage. The statistics and budgets subprojects need fast aggregate queries. Firestore's offline cache is not a truly queryable database.

## Decision
The UI reads only from **SQLDelight**. Writes are local and queued in an **outbox** that is uploaded to Firestore; remote changes come down through incremental listeners. Optimistic per-document concurrency with a chained `version`, validated in the rules: the first write to reach the server wins and the loser is stored as a recoverable conflict. The Firestore SDK's offline persistence is disabled. Details in [SYNC.md](../SYNC.md).

## Consequences
- Works 100 % offline, and balances and statistics are local SQL queries.
- The sync engine (`data:sync`) has to be built and tested thoroughly, with a fake Firestore in tests.

## Rejected alternatives
- **Firestore as the single source with its cache**: much less code, but limited queries and a poor base for subprojects 4 and 5.
- **Event sourcing** (immutable log + local projection): no conflicts and free undo, but too complex for this domain (event migrations, compaction).
- **Last-write-wins by `updatedAt`**: simpler, but silently loses edits. The chained version costs the same and loses nothing.
