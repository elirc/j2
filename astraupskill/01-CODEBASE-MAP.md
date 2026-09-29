# Read the library from its records to its commit boundary

Start with [Book](../src/librarytracker/model/Book.java) and [Borrower](../src/librarytracker/model/Borrower.java). Both are immutable, versioned values. Editing or changing availability returns a replacement object. A book's current loan fields must agree: an available book has no borrower or checkout date, and a checked-out book is active with a valid borrower identity and dates. Borrower contact normalization lives in [Values](../src/librarytracker/model/Values.java).

Next read [Loan](../src/librarytracker/model/Loan.java). It is a retained receipt with book and borrower IDs plus the title and name accepted at checkout. Those text snapshots do not follow later renames. Returning a loan produces a replacement receipt with a return date. The current book can become available while the historical loan remains queryable.

[LibraryData](../src/librarytracker/storage/LibraryData.java) validates the aggregate. It owns the workspace revision, three allocation watermarks, and immutable book, borrower, and loan lists. It checks unique IDs, unique normalized nonempty emails, references, and active-loan consistency. A book cannot claim to be checked out while its active receipt is missing, and two active loans cannot point at the same copy.

The [manager](../src/librarytracker/service/LibraryManager.java) coordinates business operations. It checks captured versions, creates candidate replacements, and commits one aggregate. The accepted in-memory `data` reference changes only after storage succeeds. Checkout inserts a receipt and replaces the book together; return updates the receipt and book together. Deletion checks retained history, while archival provides a way to retire referenced records.

The [file store](../src/librarytracker/storage/FileStorage.java) handles bounded reads, strict decoding, legacy conversion, baseline comparison, cooperative locking, and atomic replacement. If `library.snapshot` exists, it is authoritative. Otherwise both legacy files must be available together or both absent. A successful first mutation creates the snapshot without modifying either old file. This keeps conversion separate from destructive source replacement.

Finally read [App](../src/librarytracker/App.java). It handles prompts, menu choices, selection, confirmation, and controlled failures. It captures a record version before reading replacement fields. The terminal never writes a book file or borrower file directly. Option 8 reports the saved revision because the service already persists every accepted mutation.

## The CLI menu

The only place the numbering was recorded before was the stdin strings in `scripts/verify.py`. From `src/librarytracker/App.java` (the `switch` on the menu choice):

| key | action |
|---|---|
| 1 | Book b = |
| 2 | listBooks(manager.getAllBooks()); |
| 3 | Borrower b = |
| 4 | if (manager.getAllBorrowers().isEmpty()) System.out.println( |
| 5 | Book b = selectedBook(); |
| 6 | Book b = selectedBook(); |
| 7 | listBooks(manager.getCheckedOutBooks()); |
| 8 | System.out.println( |

`07-TRACE-LAB.md` asks you to drive the app by hand; use this table.

## Use tests as an index

[LibraryWorkflowTest](../test/librarytracker/LibraryWorkflowTest.java) covers the model, aggregate, service, decoder, and injected save failures. [verify.py](../scripts/verify.py) adds actual CLI inputs and competing JVM processes. Find the test for each rule as you read the implementation. For example, pair `deleteBorrower` with the history-protection test and pair the baseline comparison with the two-session test.

The original onboarding notes describe the earlier mutable model and separate files. Keep them as historical context; the source-linked course explains the current aggregate and retained-history behavior. A good codebase tour tells the reader which document matches the code they are about to change.
