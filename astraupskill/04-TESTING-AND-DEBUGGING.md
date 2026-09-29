# Test relationships and failure boundaries

Run [the verification helper](../scripts/verify.py) using Python 3 and JDK 17. It compiles into a temporary directory, executes Java checks, drives the actual CLI, and starts two JVMs for a stale-file race. Evidence goes to `.verification` or `LIBRARY_VERIFY_RESULTS_DIR`. The helper never uses your normal library directory.

## One test that asserts both sides

From `test/librarytracker/LibraryWorkflowTest.java:154`. The point is the first clause: the *old* value is still checked out after `returnBook()` returned a new one, so an accidental in-place mutation cannot pass.

```java
    test(
        "return clears current loan fields without mutating old value",
        () -> {
          Book old = new Book(0, "Title", "Author", "").checkOut(0, TODAY, null);
          Book returned = old.returnBook();
          check(
              old.isCheckedOut()
                  && !returned.isCheckedOut()
                  && returned.getBorrowerId() == null
                  && returned.getVersion() == 3);
        });
```

## Assert both sides of a checkout

A strong checkout test verifies the current book, its active receipt, and an independent reload. Merely asserting that the method returned a loan can miss a failure to persist the book. For return, check that current fields clear and the retained receipt gains a return date.

Rename the book and borrower before returning, then verify that the receipt still contains its accepted checkout text. This tests historical meaning, not just object counts. Inspect the corresponding cases in [LibraryWorkflowTest](../test/librarytracker/LibraryWorkflowTest.java).

## Reject before and during persistence

The suite rejects missing borrowers, archived records, invalid dates, stale versions, duplicate emails, inconsistent active loans, duplicate identities, invalid watermarks, and malformed snapshots. Read accepted state after each rejected mutation. An exception alone does not prove that no assignment occurred before it.

The atomic-checkout failure test substitutes a storage implementation whose replacement method throws. The candidate is valid, so this reaches the durable boundary. The test compares the original bytes and confirms that the manager exposes neither a checked-out book nor a new receipt. This catches assigning in-memory state before persistence succeeds.

## Treat legacy files as one related dataset

Both original files are required together. A missing borrower referenced by a current checkout invalidates the whole load. Strict decoding rejects unknown or duplicate fields, permissive boolean spellings, invalid dates, and missing closing separators.

The conversion test records both original byte arrays, loads a legacy checkout, returns the book, and confirms that a new snapshot exists while both old files remain unchanged. It cannot prove recovery of historical returns the old application already discarded.

## Investigate stale sessions deliberately

The two-JVM scenario makes both processes load before either saves. Exactly one accepts its book and the other reports a conflict. If both accept, inspect the lock path and baseline comparison. If both fail, inspect startup evidence rather than weakening the expected outcome.

For CLI failures, read the captured input and output and distinguish invalid input, version conflicts, reference protection, and unavailable storage. Use [the verification record](VERIFICATION.md) to report executed evidence. Proposed HTTP authorization, SQL migration, and deployment behavior need their own future tests.
