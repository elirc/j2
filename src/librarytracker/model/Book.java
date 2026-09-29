package librarytracker.model;

import java.time.LocalDate;

public final class Book {
  private final long id;
  private final int version;
  private final String title, author, isbn;
  private final boolean active, checkedOut;
  private final Long borrowerId;
  private final LocalDate checkedOutAt, dueDate;

  public Book(long id, String title, String author, String isbn) {
    this(id, 1, title, author, isbn, true, false, null, null, null);
  }

  public Book(
      long id,
      String title,
      String author,
      String isbn,
      boolean checkedOut,
      Long borrowerId,
      LocalDate checkedOutAt,
      LocalDate dueDate) {
    this(id, 1, title, author, isbn, true, checkedOut, borrowerId, checkedOutAt, dueDate);
  }

  public Book(
      long id,
      int version,
      String title,
      String author,
      String isbn,
      boolean active,
      boolean checkedOut,
      Long borrowerId,
      LocalDate checkedOutAt,
      LocalDate dueDate) {
    Values.identity(id, version);
    this.id = id;
    this.version = version;
    this.title = Values.text(title, 1, 200, "Title");
    this.author = Values.text(author, 1, 160, "Author");
    this.isbn = Values.optional(isbn, 40, "ISBN");
    if (checkedOut) {
      if (!active || borrowerId == null || borrowerId < 0)
        throw new IllegalArgumentException("Active book and borrower required for checkout.");
      Values.date(checkedOutAt);
      if (dueDate != null && (Values.date(dueDate).isBefore(checkedOutAt)))
        throw new IllegalArgumentException("Due date cannot precede checkout.");
    } else if (borrowerId != null || checkedOutAt != null || dueDate != null)
      throw new IllegalArgumentException("Available books cannot retain current loan fields.");
    this.active = active;
    this.checkedOut = checkedOut;
    this.borrowerId = borrowerId;
    this.checkedOutAt = checkedOutAt;
    this.dueDate = dueDate;
  }

  public Book edit(String title, String author, String isbn) {
    return new Book(
        id,
        Values.nextVersion(version),
        title,
        author,
        isbn,
        active,
        checkedOut,
        borrowerId,
        checkedOutAt,
        dueDate);
  }

  public Book setActive(boolean value) {
    if (value == active) return this;
    if (checkedOut) throw new IllegalStateException("Return this book before archiving it.");
    return new Book(
        id, Values.nextVersion(version), title, author, isbn, value, false, null, null, null);
  }

  public Book checkOut(long borrower, LocalDate at, LocalDate due) {
    if (!active || checkedOut)
      throw new IllegalStateException("Book is archived or already checked out.");
    return new Book(
        id, Values.nextVersion(version), title, author, isbn, true, true, borrower, at, due);
  }

  public Book returnBook() {
    if (!checkedOut) throw new IllegalStateException("Book is not checked out.");
    return new Book(
        id, Values.nextVersion(version), title, author, isbn, active, false, null, null, null);
  }

  public long getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public String getTitle() {
    return title;
  }

  public String getAuthor() {
    return author;
  }

  public String getIsbn() {
    return isbn;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isCheckedOut() {
    return checkedOut;
  }

  public Long getBorrowerId() {
    return borrowerId;
  }

  public LocalDate getCheckedOutAt() {
    return checkedOutAt;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public boolean isOverdue(LocalDate today) {
    return checkedOut && dueDate != null && dueDate.isBefore(today);
  }
}
