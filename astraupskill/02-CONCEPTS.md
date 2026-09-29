# Concepts behind a consistent checkout

## Related records form a consistency boundary

A checkout is not just `book.checkedOut = true`. It must refer to an existing active borrower and produce one active loan receipt for the same copy. The [aggregate validator](../src/librarytracker/storage/LibraryData.java) checks that the book's borrower, checkout date, and due date agree with its active loan. This makes inconsistent restored data a controlled load failure rather than an invisible application assumption.

In a SQL design, foreign keys could establish that the referenced book and borrower exist, while a unique constraint or partial index could limit active loans per copy. Some business rules would still belong in the service. The file implementation keeps those checks visible so you can identify which guarantees a database can enforce and which require policy.

## Two id bases, on purpose

Book and borrower ids may be 0 (`Values.identity(id, version)` in `Book.java`/`Borrower.java` checks the version, not the id), while loan ids must be at least 1 (`Loan.java`: `Values.identity(id, 1)`). The difference is a boundary worth a test: a loan is created by the system and 0 is reserved as "no loan yet", whereas books and borrowers are imported with whatever ids the source had. Write the test that constructs `new Loan(0, ...)` and expects the validation error, next to one that accepts `new Book(0, ...)`; put both in the model test class.

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

*Consistency boundary*: the set of records one action must change together (book, loan, borrower). *Snapshot*: the whole state written as one file, replaced atomically. *Baseline*: the bytes the app last read; compared before overwriting. *Stale edit*: a write based on a baseline that storage has moved past. *Receipt*: the copy of facts (title, name, due date) frozen into the loan when it was created. *Archival*: hiding a record without deleting the history that references it. *Row version / concurrency token*: the C# equivalent of the baseline compare (see 08).

