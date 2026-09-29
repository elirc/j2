package librarytracker.model;

import java.time.LocalDate;

public record Loan(
    long id,
    long bookId,
    long borrowerId,
    String bookTitle,
    String borrowerName,
    LocalDate checkedOutAt,
    LocalDate dueDate,
    LocalDate returnedAt) {
  public Loan {
    Values.identity(id, 1);
    Values.identity(bookId, 1);
    Values.identity(borrowerId, 1);
    bookTitle = Values.text(bookTitle, 1, 200, "Accepted book title");
    borrowerName = Values.text(borrowerName, 1, 160, "Accepted borrower name");
    Values.date(checkedOutAt);
    if (dueDate != null && Values.date(dueDate).isBefore(checkedOutAt))
      throw new IllegalArgumentException("Due date precedes checkout.");
    if (returnedAt != null && Values.date(returnedAt).isBefore(checkedOutAt))
      throw new IllegalArgumentException("Return date precedes checkout.");
  }

  public Loan returned(LocalDate at) {
    if (returnedAt != null) throw new IllegalStateException("Loan already returned.");
    return new Loan(id, bookId, borrowerId, bookTitle, borrowerName, checkedOutAt, dueDate, at);
  }
}
