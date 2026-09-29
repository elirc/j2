# Practice: grow the library toward a CRUD web service

Every exercise here touches at least two of the three records at once (a book, its loan, the borrower), so start each one by writing down which of the three change and which must not, and what the reader sees if the second write fails after the first. That is the library-specific problem; the generic advice follows. Use a separate branch or copy and fictional data. Write acceptance criteria before coding, including failure cases and the existing invariants that must remain true. These exercises build on the current design instead of replacing it without a behavioral contract.

## Exercise 1: an overdue projection

Add a view of overdue active loans sorted by due date and stable book ID. Use an explicit comparison date or injected clock so tests remain deterministic. Books without a due date and returned loans do not belong in the result.

For example, with the clock on September 10, a loan due September 9 appears, while one due September 10 does not. Test equal dates to establish stable ordering. Avoid storing an overdue boolean that becomes stale tomorrow; derive it from accepted dates and the comparison date.

## Exercise 2: renew a loan

Decide whether renewal replaces the current due date or also retains a renewal event. Preserve the original checkout date. Require current editing context, reject returned loans, and specify whether shortening a due date is allowed.

Update the book's current due date and active receipt consistently in one aggregate. Test stale renewal, invalid dates, a returned loan, and an injected save failure. After failure, independently reload and compare both dates. If renewal events change the storage schema, introduce an explicit format version and migration behavior.

## Exercise 3: a narrow HTTP adapter

Design routes for books, borrowers, checkout, return, and history. Accept strict request DTOs and expected versions. The server chooses its configured storage directory; clients must never supply arbitrary filesystem paths. Distinguish validation, missing records, permission failures, conflicts, and unavailable persistence in responses.

Add authentication and authorization before exposing borrower contact details. The CLI's assumption of local machine access is not a web ownership model. Test direct requests that bypass the UI. A browser editor should retain typed values and the original version after conflicts, with a deliberate export and reload path.

## Exercise 4: relational migration

Sketch book-copy, borrower, loan, and optional renewal tables. Use foreign keys, unique normalized nonempty email constraints, and a rule limiting each copy to one active loan. Preserve accepted title/name snapshots in the loan row and expected-version checks in mutations.

Build a dry-run importer into a new destination. Validate the source and report record counts and rejected references before writing. Keep the original text files and snapshot unchanged. Compare checkout, return, archival, and failed-save behavior against the existing suite, then add database-specific transaction and constraint tests.

Finish each exercise with a short review: concrete behavior, implementation boundary, evidence, and remaining limits. That makes the work useful practice in feature ownership rather than a collection of additional menu options.
