# Rules-only exact-handle directory experiment (draft; not deployed)

This is an isolated candidate based on `Gemesfornite12/Beta-app-` `main` at the experiment's parent commit. It adds a new `/social_test/memberDirectory` sibling and leaves the legacy `/social_test/directory` and all chat rules unchanged. It is not the project's active `database.rules.json` and is not approved for deployment.

## Finding: a safe rules-only contract is possible, with a staged transition

Canonical records are `/social_test/memberDirectory/byHandle/{normalizedHandle} = {uid, handle}`. The private single-valued reverse reservation is `/social_test/memberDirectory/byUid/{uid} = normalizedHandle`. The reverse slot is the one-handle-per-UID lock; the canonical handle child is the one-UID-per-handle compare-and-create point. Auth UID is derived solely from `auth.uid`/the UID path and profile key/value, never a client-supplied owner argument.

The reservation workflow is deliberately staged:

1. After explicit opt-in and client normalization/grammar validation, run a transaction at the caller's exact `byUid/{auth.uid}` slot, changing only `null -> handle`. Rules accept creation only for that UID, with a matching `/social_test/profiles/{uid}.uid`, a normalized handle, and no already-claimed canonical handle.
2. Create `byHandle/{handle}` only when it is absent, the new record's UID is `auth.uid`, and the committed reverse slot equals that handle. Concurrent claims for the same handle serialize/conflict at that canonical child; only one create can pass.
3. Revoke in fail-closed order: delete the canonical mapping as its owner, then delete the private reverse slot. A reverse slot can be cleared only after its matching public mapping is absent or belongs to someone else. If interrupted, the public mapping is either still reserved or absent; a stale private slot blocks only that user's next opt-in and can be safely cleared. A user cannot change an occupied reverse slot or hold two active mappings.

A single multipath update that tries to create both records from an empty state is *not* the authorization primitive here: child-level `root` checks observe the pre-write state, so the new reverse pointer cannot authorize its sibling handle create in that same update. The design relies on a committed per-UID RTDB transaction, then a separate rules-checked canonical create. This permits temporary private reservation/cleanup state, but never permits two active handles for one UID or two owners for one handle. Directory mappings are not a distributed atomic pair during that short interval; clients must handle/recover stale reservations idempotently. This is a deliberate, fail-closed tradeoff, not an assertion that transactions are unnecessary.

Exact point reads are allowed only on authenticated `byHandle/{handle}` records; no read grant exists at `memberDirectory`, `byHandle`, or collection levels. `/byUid/{uid}` is readable only by that UID. Records contain only `uid` and normalized `handle`. Exact reads remain enumerable by probing predictable strings; the design does not claim otherwise. Explicit user consent is a client/UI requirement (rules can enforce UID ownership and state shape, not that a human saw a dialog). Profile existence is checked by the reverse-slot create rule. App Check / rate limits are not implemented here.

Directory lookup only resolves an opted-in UID. It grants no chat membership or write authority. Checked-in `/chats/$chatId/.write = auth != null` is intentionally untouched and remains a separate blocker for secure group authorization.

## Local validation status

An Emulator Suite rules test file is included under `test/`. No emulator/Node/Firebase CLI is available in this workspace, so tests were not executed. No live RTDB access, live reads/writes, deployment, CI, merge, or production changes were made. Before implementation, run these tests in a disposable local Emulator Suite project and review transaction/rules behavior against the exact SDK/rules versions used by the app.
