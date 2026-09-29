# Worked change: checkout with a complete retained receipt

The story is: "A librarian can lend an available copy to an active borrower, and the accepted checkout remains understandable after later profile edits." A missing borrower, archived record, invalid due date, stale version, or failed save must not leave a checked-out book without a receipt.

## Capture the caller's editing context

The service accepts a book ID, the version displayed before the command, a borrower ID, and an optional due date:

```java
Loan receipt = manager.checkOutBook(
    0,                 // stable copy ID
    1,                 // version the caller reviewed
    0,                 // existing borrower ID
    LocalDate.of(2026, 9, 16)
);
```

This example assumes a fictional fixture with matching records and clock. The checkout date comes from the injected clock. The caller cannot replace the accepted book title, borrower name, or allocated loan ID through this command. Those values come from current validated records.

## Prepare one candidate aggregate

Read `checkOutBook` in [LibraryManager](../src/librarytracker/service/LibraryManager.java). It checks the captured book version and finds an active borrower. The immutable book rejects an archived or already checked-out state and validates the dates. The service creates a [Loan](../src/librarytracker/model/Loan.java) containing the current title and borrower name as accepted receipt text.

The candidate replaces the book and appends the receipt together. [LibraryData](../src/librarytracker/storage/LibraryData.java) validates that one active loan agrees with the book's borrower, checkout date, and due date. A relationship is not valid merely because each individual record can be constructed.

## Cross the commit boundary

The [store](../src/librarytracker/storage/FileStorage.java) checks the loaded baseline under a cooperative lock, writes a complete temporary snapshot, flushes it, and atomically replaces the active file. Only after storage succeeds does the manager assign the candidate as accepted in-memory state.

The failure test injects an exception immediately before replacement. It independently checks the old bytes, the available book, and the absence of a new receipt. If the manager assigned its candidate before saving, the terminal could show a checkout that disappears after restart.

## Return without erasing history

Return replaces the current book with an available version and marks the corresponding receipt returned. The accepted title and borrower name remain unchanged even after current records are renamed. That retained relationship explains why deletion is blocked and archival is the appropriate retirement workflow.

In a review, identify the input boundary, version rule, historical snapshots, aggregate invariant, and commit point. Also state the limits: there is no authenticated web actor yet, and returns discarded before legacy import cannot be reconstructed from missing data.
