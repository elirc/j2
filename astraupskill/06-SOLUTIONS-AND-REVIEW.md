# Solution guidance and engineering review

Read this after attempting [the exercises](05-PRACTICE.md). Evaluate the reasoning and evidence, not the number of abstractions introduced. A small change with a clear contract is easier to trust than a large rewrite whose failure behavior is unclear.

## The overdue rule as delivered

The whole rule is three conditions, `src/librarytracker/model/Book.java:135`. Nothing is stored: overdue is derived from an explicit comparison date every time it is asked for.

```java
  public boolean isOverdue(LocalDate today) {
    return checkedOut && dueDate != null && dueDate.isBefore(today);
  }
```

## Exercise 1: overdue projection

A passing solution has two small pieces. The service method reuses the model rule and sorts with an explicit tie-break:

```java
public synchronized List<Book> getOverdueBooks(LocalDate today) {
  return getAllBooks().stream()
      .filter(b -> b.isOverdue(today))
      .sorted(Comparator.comparing(Book::getDueDate).thenComparingLong(Book::getId))
      .toList();
}
```

The CLI case is one line, `case "19" -> listBooks(manager.getOverdueBooks(LocalDate.now()));`. It reuses `listBooks` so each row keeps the `(OVERDUE)` suffix the check looks for. `isOverdue` already returns false for books without a due date, so the filter needs no null guard. The `thenComparingLong` matters: without it, books 1 and 2 (same due date) keep whatever order `getAllBooks` produced, which happens to be id order today and would silently change if that method's sort changed. Do not persist an overdue boolean: time makes it wrong without any user edit.

## Exercise 2: renewal touches two records

Add `Book.renew(LocalDate due)`. It throws if the book is not checked out, and otherwise returns a new `Book` with `nextVersion`, the same borrower and checkout date, and the new due date. The `Book` constructor then re-applies the "due cannot precede checkout" rule for you. In the service, find the active loan exactly as `returnBook` does (`LibraryManager.java:246-250`), build a replacement `Loan` with the new `dueDate` (the record's constructor re-validates it), and pass both replacements to one `commit`. If you update only the book, the check still fails, but for a better reason than a test: `LibraryData.java:56` rejects the candidate before anything is written.

Replacing the due date loses the earlier promise. If users must explain extensions, retain events (old date, new date, accepted time, actor). The CLI has no authenticated actor, so a typed name is not a substitute for identity.

## Exercises 3 and 4: rules and importer

The reference port that passes `ex3-rules.test.mjs` is about thirty lines: build `Map`s of books and borrowers, check ids against watermarks while inserting, collect active loans per `bookId`, then walk the books comparing `checkedOut` with the presence of an active loan. That is the same order of operations as `LibraryData.java:26-59`, and keeping the order makes the error you see for a given bad file match the Java one.

For the importer, split on `
`, require `LIBRARY2` and a final empty line, decode each `B`/`P`/`L` row by position, and compare the `END` line to the counts you built. A hand-rolled importer most often goes wrong by trusting the trailer instead of recomputing it. The second check in `ex4-import.test.mjs` exists to catch exactly that.

## Transport introduces new boundaries

An HTTP route should validate its request, obtain the current actor, call a policy-aware service, and translate outcomes. Clients should not choose server-owned loan IDs, accepted names, or storage paths. Missing data, stale versions, and storage outages deserve distinct responses.

Preserve a browser draft's original version after a conflict. Automatically fetching the latest version and retrying hides the overwrite decision. Disabling a submit button also does not provide durable idempotency when a checkout response disappears after commit; a web extension should consider request receipts and retained retry keys.

## SQL preserves history only if the schema does

Foreign keys protect references, and a unique active-loan rule can prevent lending the same copy twice. Retain accepted title and borrower-name snapshots. Cascading deletion could erase promised history, so deletion and archival behavior need a deliberate schema policy.

Transactions do not replace authorization or expected-version checks. Inspect affected-row counts and distinguish a missing record from stale context. Use the current service tests as a behavioral contract, then add migration and database-specific cases.

## Review findings on the delivered code (verified 2026-10-06)

Each item cites code you can open. None of these is a crash, but each would matter when this core sits behind a web adapter.

1. **Checkout does not take the borrower's version.** `checkOutBook(long bookId, int bookVersion, long borrowerId, LocalDate due)` (`LibraryManager.java:219-225`) checks the *book* version but looks the borrower up by id alone, then copies `person.getName()` into the receipt (`:229-231`). In the CLI the gap between typing the id and committing is a few keystrokes, and a second session is caught by the file baseline. In a web form the librarian could approve "Sam Reader" while the receipt records a name changed after the page loaded. Fix: accept `borrowerVersion` and compare it the way `borrower(id, version)` does (`:87-93`).
2. **The overdue label ignores the injected clock.** `LibraryManager` takes a `Clock` (`:18`) and uses it for checkout and return dates (`:227`, `:251`), but `App.book()` computes `(OVERDUE)` with `LocalDate.now()` (`App.java:239`). In production both read the system clock, but a test that injects a fixed clock cannot control what the CLI labels as overdue. Exercise 1 has the same choice to make; pass the date from one place.
3. **Every failure message says "Change not accepted".** The single catch at `App.java:176-180` also wraps read-only commands, so a search typed with status `Available` prints `Change not accepted: Invalid search or status.` (`searchBooks` compares status case-sensitively, `LibraryManager.java:262-265`). The wording is harmless here but is the CLI version of returning 409 for a 400. Map read and write failures separately in an HTTP adapter.
4. **Why `FileStorage` holds two locks (a design point, not a bug).** `save` first synchronizes on a per-path monitor from the static `LOCKS` map, then takes the OS file lock (`FileStorage.java:78-82`). `FileChannel.lock()` locks on behalf of the whole JVM, and a second overlapping lock attempt from the same JVM throws `OverlappingFileLockException` instead of waiting. The monitor serializes threads inside one process, and the file lock coordinates separate processes. The two-JVM check in `verify.py` covers only the second case.

Before requesting review, explain one concrete before/after behavior, the invariant, the commit point, and the evidence actually run. Mention limits such as absent ownership policy, manual conflict reconciliation, or unverified deployment. Accurate boundaries demonstrate the judgment expected beyond junior CRUD implementation.
