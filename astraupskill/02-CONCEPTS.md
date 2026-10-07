# Concepts behind a consistent checkout

## Related records form a consistency boundary

A checkout is not just `book.checkedOut = true`. It must refer to an existing active borrower and produce one active loan receipt for the same copy. The [aggregate validator](../src/librarytracker/storage/LibraryData.java) checks that the book's borrower, checkout date, and due date agree with its active loan. This makes inconsistent restored data a controlled load failure rather than an invisible application assumption.

In a SQL design, foreign keys could establish that the referenced book and borrower exist, while a unique constraint or partial index could limit active loans per copy. Some business rules would still belong in the service. The file implementation keeps those checks visible so you can identify which guarantees a database can enforce and which require policy.

## One id base, zero, for all three records

`Values.identity(id, version)` (`Values.java:32-35`) rejects `id < 0`, `id == Long.MAX_VALUE`, and `version < 1`. Books and borrowers pass their real version. `Loan` has no version, so its compact constructor calls `Values.identity(id, 1)` (`Loan.java:15-17`) with a dummy version of 1 just to reuse the id rule for `id`, `bookId`, and `borrowerId`. Zero is therefore a valid id for every record type: `LibraryData.empty()` starts all three watermarks at 0, legacy import numbers its synthesized loans from `loans.size()` (`FileStorage.java:365-366`), and the first checkout in a new workspace prints `Loan 0.` (the suite's "loan ids advance across return and restart" test expects the *second* loan to be id 1).

The `1` in `identity(id, 1)` is easy to misread as "loan ids start at 1". When a helper is reused with a placeholder argument, a named overload such as `Values.id(long)` would make the intent explicit. The zero base matters again in [08](08-TRANSLATION.md): SQL identity columns usually start at 1, so an importer must map ids rather than assume they line up.

## Current values and historical snapshots answer different questions

The current borrower profile answers "What is this person's name now?" A loan receipt answers "What name was accepted when this checkout occurred?" The receipt retains both a borrower ID and the accepted name. Renaming the profile preserves the reference while leaving the receipt text unchanged.

For example, a checkout records book title "Reliable systems" and borrower "Sam Reader." Later edits may produce "Reliable systems, second edition" and "Sam Rivera." The old loan should still show its accepted values. This does not imply that all history must contain complete entity copies; only retain fields needed to explain the accepted event.

## Deletion and archival have different purposes

Deleting an unused book or borrower is straightforward because no loan refers to it. Once history exists, deletion is rejected. Archival changes current availability while preserving relationships. Active loans must be returned before a book or borrower can be archived, and archived records cannot participate in new checkout operations.

This pattern appears in many CRUD applications: customers with invoices, products with order lines, and employees with approvals often need retirement rather than destructive removal. The decision comes from the domain's historical requirements, not from a universal rule that every record should be soft-deleted.

## Atomicity and stale-edit protection are separate

The file store writes one complete snapshot and replaces it atomically. That protects the relationship between book state and loan history during a normal save. Baseline comparison under a cooperative lock protects against a session saving from an old workspace read. Record versions protect edits within the service's accepted state.

A transaction alone cannot tell whether a user's input was based on old content. A future browser editor should send the version it originally read and preserve its draft after a conflict. Refreshing the version automatically before retrying would hide the overwrite decision from the user.

## Capacity and validation are part of the contract

The implementation bounds files to 10 MiB and each collection to 10,000 records. It validates UTF-8, exact fields, booleans, IDs, dates, trailers, references, and allocation watermarks. ISBN is bounded metadata, and email validation is a basic local format rule. Neither is advertised as external verification. Clear limits make a small project easier to reason about and provide concrete reasons for a later SQL migration.

## Glossary

*Consistency boundary*: the set of records one action must keep consistent. Checkout and return write the book and its loan; the borrower is read and must be active, but is not rewritten. *Snapshot*: the whole state written as one file, replaced atomically. *Baseline*: the bytes the app last read; compared before overwriting. *Stale edit*: a write based on a baseline that storage has moved past. *Receipt*: the copy of facts (title, name, due date) frozen into the loan when it was created. *Archival*: hiding a record without deleting the history that references it. *Row version / concurrency token*: the EF Core equivalent of the per-record `version` check (see 08).

