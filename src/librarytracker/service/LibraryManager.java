package librarytracker.service;

import java.io.IOException;
import java.time.*;
import java.util.*;
import librarytracker.model.*;
import librarytracker.storage.*;

public final class LibraryManager {
  private final FileStorage storage;
  private final Clock clock;
  private LibraryData data;

  public LibraryManager(FileStorage storage) throws IOException {
    this(storage, Clock.systemDefaultZone());
  }

  public LibraryManager(FileStorage storage, Clock clock) throws IOException {
    this.storage = storage;
    this.clock = clock;
    data = storage.load();
  }

  public synchronized void reload() throws IOException {
    data = storage.load();
  }

  public synchronized long revision() {
    return data.revision();
  }

  private void commit(
      List<Book> books,
      List<Borrower> borrowers,
      List<Loan> loans,
      long nextBook,
      long nextBorrower,
      long nextLoan)
      throws IOException {
    if (data.revision() == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted.");
    LibraryData candidate =
        new LibraryData(
            data.revision() + 1, nextBook, nextBorrower, nextLoan, books, borrowers, loans);
    storage.save(candidate);
    data = candidate;
  }

  private static void allocatable(long id) {
    if (id == Long.MAX_VALUE) throw new IllegalStateException("Ids exhausted.");
  }

  public synchronized List<Book> getAllBooks() {
    return data.books().stream().sorted(Comparator.comparingLong(Book::getId)).toList();
  }

  public synchronized List<Borrower> getAllBorrowers() {
    return data.borrowers().stream().sorted(Comparator.comparingLong(Borrower::getId)).toList();
  }

  public synchronized List<Loan> getLoanHistory() {
    return data.loans().stream().sorted(Comparator.comparingLong(Loan::id).reversed()).toList();
  }

  public synchronized List<Book> getCheckedOutBooks() {
    return getAllBooks().stream().filter(Book::isCheckedOut).toList();
  }

  public synchronized Optional<Book> findBookById(long id) {
    return data.books().stream().filter(b -> b.getId() == id).findFirst();
  }

  public synchronized Optional<Borrower> findBorrowerById(long id) {
    return data.borrowers().stream().filter(b -> b.getId() == id).findFirst();
  }

  public synchronized Optional<Borrower> findBorrowerForBook(Book book) {
    return book.getBorrowerId() == null ? Optional.empty() : findBorrowerById(book.getBorrowerId());
  }

  private Book book(long id, int version) {
    Book b = findBookById(id).orElseThrow(() -> new IllegalArgumentException("Book not found."));
    if (b.getVersion() != version)
      throw new IllegalStateException("Book version changed. Reload and review.");
    return b;
  }

  private Borrower borrower(long id, int version) {
    Borrower b =
        findBorrowerById(id).orElseThrow(() -> new IllegalArgumentException("Borrower not found."));
    if (b.getVersion() != version)
      throw new IllegalStateException("Borrower version changed. Reload and review.");
    return b;
  }

  private List<Book> replaceBook(Book replacement) {
    return data.books().stream()
        .map(b -> b.getId() == replacement.getId() ? replacement : b)
        .toList();
  }

  private List<Borrower> replaceBorrower(Borrower replacement) {
    return data.borrowers().stream()
        .map(b -> b.getId() == replacement.getId() ? replacement : b)
        .toList();
  }

  public synchronized Book addBook(String title, String author, String isbn) throws IOException {
    allocatable(data.nextBookId());
    Book b = new Book(data.nextBookId(), title, author, isbn);
    var books = new ArrayList<>(data.books());
    books.add(b);
    commit(
        books,
        data.borrowers(),
        data.loans(),
        data.nextBookId() + 1,
        data.nextBorrowerId(),
        data.nextLoanId());
    return b;
  }

  public synchronized Borrower registerBorrower(String name, String email, String phone)
      throws IOException {
    allocatable(data.nextBorrowerId());
    Borrower b = new Borrower(data.nextBorrowerId(), name, email, phone);
    var borrowers = new ArrayList<>(data.borrowers());
    borrowers.add(b);
    commit(
        data.books(),
        borrowers,
        data.loans(),
        data.nextBookId(),
        data.nextBorrowerId() + 1,
        data.nextLoanId());
    return b;
  }

  public synchronized Book editBook(long id, int version, String title, String author, String isbn)
      throws IOException {
    Book b = book(id, version).edit(title, author, isbn);
    commit(
        replaceBook(b),
        data.borrowers(),
        data.loans(),
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId());
    return b;
  }

  public synchronized Borrower editBorrower(
      long id, int version, String name, String email, String phone) throws IOException {
    Borrower b = borrower(id, version).edit(name, email, phone);
    commit(
        data.books(),
        replaceBorrower(b),
        data.loans(),
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId());
    return b;
  }

  public synchronized void deleteBook(long id, int version) throws IOException {
    book(id, version);
    if (data.loans().stream().anyMatch(l -> l.bookId() == id))
      throw new IllegalStateException("Book has loan history. Archive it instead.");
    commit(
        data.books().stream().filter(b -> b.getId() != id).toList(),
        data.borrowers(),
        data.loans(),
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId());
  }

  public synchronized void deleteBorrower(long id, int version) throws IOException {
    borrower(id, version);
    if (data.loans().stream().anyMatch(l -> l.borrowerId() == id))
      throw new IllegalStateException("Borrower has loan history. Archive the borrower instead.");
    commit(
        data.books(),
        data.borrowers().stream().filter(b -> b.getId() != id).toList(),
        data.loans(),
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId());
  }

  public synchronized void setBookActive(long id, int version, boolean active) throws IOException {
    Book old = book(id, version), b = old.setActive(active);
    if (b != old)
      commit(
          replaceBook(b),
          data.borrowers(),
          data.loans(),
          data.nextBookId(),
          data.nextBorrowerId(),
          data.nextLoanId());
  }

  public synchronized void setBorrowerActive(long id, int version, boolean active)
      throws IOException {
    Borrower old = borrower(id, version);
    if (!active
        && data.loans().stream().anyMatch(l -> l.borrowerId() == id && l.returnedAt() == null))
      throw new IllegalStateException("Return this borrower's active loans before archiving.");
    Borrower b = old.setActive(active);
    if (b != old)
      commit(
          data.books(),
          replaceBorrower(b),
          data.loans(),
          data.nextBookId(),
          data.nextBorrowerId(),
          data.nextLoanId());
  }

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

  public synchronized void returnBook(long bookId, int version) throws IOException {
    Book b = book(bookId, version).returnBook();
    Loan loan =
        data.loans().stream()
            .filter(l -> l.bookId() == bookId && l.returnedAt() == null)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Active loan missing."));
    Loan returned = loan.returned(LocalDate.now(clock));
    commit(
        replaceBook(b),
        data.borrowers(),
        data.loans().stream().map(l -> l.id() == loan.id() ? returned : l).toList(),
        data.nextBookId(),
        data.nextBorrowerId(),
        data.nextLoanId());
  }

  public synchronized List<Book> searchBooks(String query, String status) {
    if (query == null
        || query.length() > 200
        || !List.of("all", "available", "out", "archived").contains(status))
      throw new IllegalArgumentException("Invalid search or status.");
    String q = query.strip().toLowerCase(Locale.ROOT);
    return getAllBooks().stream()
        .filter(
            b ->
                status.equals("all")
                    || (status.equals("archived")
                        ? !b.isActive()
                        : b.isActive() && (status.equals("out") == b.isCheckedOut())))
        .filter(
            b ->
                (b.getTitle() + " " + b.getAuthor() + " " + Objects.toString(b.getIsbn(), ""))
                    .toLowerCase(Locale.ROOT)
                    .contains(q))
        .toList();
  }
}
