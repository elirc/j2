# Trace lab: current loan state and historical meaning

Use a new fictional data directory. Record IDs, versions, snapshot revisions, and terminal messages. This lab connects visible workflows to immutable values, aggregate validation, and the persistence boundary.

## Where the receipt gets its accepted text

This is `LibraryManager.checkOutBook`, `src/librarytracker/service/LibraryManager.java:219`. The title and borrower name written into the `Loan` come from the validated `Book` and `Borrower` records, never from the command input.

```java
  public synchronized Loan checkOutBook(
      long bookId, int bookVersion, long borrowerId, LocalDate due) throws IOException {
    Book old = book(bookId, bookVersion);
    Borrower person =
        findBorrowerById(borrowerId)
            .orElseThrow(() -> new IllegalArgumentException("Borrower not found."));
    if (!person.isActive()) throw new IllegalStateException("Borrower is archived.");
    allocatable(data.nextLoanId());
    LocalDate at = LocalDate.now(clock);
    Book b = old.checkOut(borrowerId, at, due);
    Loan loan =
        new Loan(
            data.nextLoanId(), bookId, borrowerId, b.getTitle(), person.getName(), at, due, null);
    var loans = new ArrayList<>(data.loans());
    loans.add(loan);
    commit(
        replaceBook(b),
        data.borrowers(),
        loans,
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId() + 1);
    return loan;
  }
```

## Checkout and rename

Create a book named "Reliable systems" and a borrower named "Sam Reader." Check out the book with a valid optional due date. Inspect active loans and retained history. Explain where the receipt's accepted title and borrower name came from: the service reads current validated records rather than trusting arbitrary checkout fields.

Edit the current title and borrower name. The catalogue and profile should show the new values, while history retains the original accepted text. Trace [Loan](../src/librarytracker/model/Loan.java) and [LibraryManager](../src/librarytracker/service/LibraryManager.java) to explain why those values differ intentionally while their ID references remain valid.

## Return, protect, and archive

Return the book. Current borrower and date fields clear, while the receipt receives a return date. Attempt to delete the book and borrower. Both are protected because retained history refers to them. Archive them instead, confirm that archived participants cannot be used for checkout, and then restore them.

Find the related checks in [LibraryData](../src/librarytracker/storage/LibraryData.java). An active loan must agree with its book and refer to an active borrower. These rules apply to loaded snapshots as well as commands. Stored data is not trustworthy merely because it was previously written to a file.

## Competing sessions and failed persistence

Open two sessions before either makes a new change. Save a book in the first and attempt another in the second. Reload the rejected session, inspect accepted records, and deliberately re-enter the change. Explain how the lock coordinates saves and the baseline comparison detects an old read.

Read the injected checkout-failure test. It fails before atomic replacement and verifies an available book, no receipt, and unchanged bytes. State what would go wrong if the manager exposed its candidate before saving it.

## Legacy conversion

Inspect the disposable legacy fixture. A current checkout becomes an active receipt with names available at import. The first accepted mutation creates `library.snapshot` without modifying either original text file. Older returns cannot be reconstructed because the source no longer contains them.

Finish with a five-minute walkthrough covering command input, captured version, policy checks, candidate aggregate, commit point, history, and recovery. Use [the verification record](VERIFICATION.md) to support observed behavior and identify a concrete next step toward an authenticated SQL-backed web application.
