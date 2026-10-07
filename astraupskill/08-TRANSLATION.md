# 08. Translation: the same design in C# and EF Core

[Course index](README.md)

| here (Java, file snapshots) | C# / EF Core |
|---|---|
| `Book`, `Borrower`, `Loan` value objects with validated constructors | `record Book(int Id, int Version, ...)` with validation in the constructor (`record` gives value equality and immutability); EF Core maps records fine when you keep a private parameterless constructor or use `[SetsRequiredMembers]` |
| the loan receipt: title, borrower name and due date copied into the loan when it is created | denormalised accepted columns on the `Loans` table (`BookTitleAtLoan`, `BorrowerNameAtLoan`) written in the same `SaveChanges` as the loan; a join to `Books` would show the *current* title, which is the wrong answer for a receipt |
| the per-record `version` the caller captured (`LibraryManager.book(id, version)`), backed by the whole-file baseline compare in `FileStorage.save` | a row-version concurrency token: `[Timestamp] byte[] RowVersion` (SQL Server) or `[ConcurrencyCheck] int Version`; EF issues `UPDATE ... WHERE Id = @p AND Version = @expected` and throws `DbUpdateConcurrencyException` when zero rows match |
| checkout: replace the book + append the loan, after reading the borrower | one `DbContext`, two tracked changes (book update, loan insert), one `SaveChangesAsync()`; a single `SaveChanges` call is already wrapped in a transaction, so either both rows change or neither does |
| archival instead of deletion | `ArchivedAt DateTime?` plus a global query filter `HasQueryFilter(b => b.ArchivedAt == null)`; loans keep their foreign key |
| ids: books, borrowers, and loans all start at 0 (`LibraryData.empty()`) | identity columns usually start at 1; the import path maps source ids to new keys and stores the source id in a separate column |

## The one thing to keep

The receipt rule. Juniors reach for a join because it avoids "duplicate data"; the course's point is that a loan must remember what was true when it was created. In EF Core that is a plain column, not a navigation property.

## Exercise

Model the three entities and the checkout action in EF Core with an in-memory SQLite provider; write the test that opens two contexts, loads the same book in both, checks it out in the first, and asserts the second gets `DbUpdateConcurrencyException`.
