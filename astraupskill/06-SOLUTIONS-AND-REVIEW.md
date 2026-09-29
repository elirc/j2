# Solution guidance and engineering review

Read this after attempting [the exercises](05-PRACTICE.md). Evaluate the reasoning and evidence, not the number of abstractions introduced. A small change with a clear contract is easier to trust than a large rewrite whose failure behavior is unclear.

## The overdue rule as delivered

The whole rule is three conditions, `src/librarytracker/model/Book.java:134`. Nothing is stored: overdue is derived from an explicit comparison date every time it is asked for.

```java
  public boolean isOverdue(LocalDate today) {
    return checkedOut && dueDate != null && dueDate.isBefore(today);
  }
```

## Derive overdue state

Use the accepted due date and an explicit comparison date. The existing rule uses `isBefore`, so a book due today is not overdue. If the product wants a different rule, name and test that behavior change. Equal due dates need a stable secondary sort.

Do not persist a simple overdue boolean without a refresh policy. Time can make it wrong even when no user edits the loan. This same reasoning applies to web dashboards and cached projections.

## Renewal requires a history decision

Replacing both current due-date fields preserves consistency but loses the earlier promise. If users need to explain extensions, retain events containing old date, new date, accepted time, and a trustworthy actor. The CLI has no authenticated actor, so an arbitrary input string is not a substitute for identity.

Reject stale renewal context and commit all related changes together. Independently reload after an injected failure. A new format header should distinguish additional renewal fields; silently accepting arbitrary extra columns would weaken corruption detection and compatibility reasoning.

## Transport introduces new boundaries

An HTTP route should validate its request, obtain the current actor, call a policy-aware service, and translate outcomes. Clients should not choose server-owned loan IDs, accepted names, or storage paths. Missing data, stale versions, and storage outages deserve distinct responses.

Preserve a browser draft's original version after a conflict. Automatically fetching the latest version and retrying hides the overwrite decision. Disabling a submit button also does not provide durable idempotency when a checkout response disappears after commit; a web extension should consider request receipts and retained retry keys.

## SQL preserves history only if the schema does

Foreign keys protect references, and a unique active-loan rule can prevent lending the same copy twice. Retain accepted title and borrower-name snapshots. Cascading deletion could erase promised history, so deletion and archival behavior need a deliberate schema policy.

Transactions do not replace authorization or expected-version checks. Inspect affected-row counts and distinguish a missing record from stale context. Use the current service tests as a behavioral contract, then add migration and database-specific cases.

Before requesting review, explain one concrete before/after behavior, the invariant, the commit point, and the evidence actually run. Mention limits such as absent ownership policy, manual conflict reconciliation, or unverified deployment. Accurate boundaries demonstrate the judgment expected beyond junior CRUD implementation.
