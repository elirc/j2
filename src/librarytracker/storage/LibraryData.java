package librarytracker.storage;

import java.util.*;
import librarytracker.model.*;

public record LibraryData(
    long revision,
    long nextBookId,
    long nextBorrowerId,
    long nextLoanId,
    List<Book> books,
    List<Borrower> borrowers,
    List<Loan> loans) {
  public LibraryData {
    books = List.copyOf(books);
    borrowers = List.copyOf(borrowers);
    loans = List.copyOf(loans);
    if (revision < 0
        || nextBookId < 0
        || nextBorrowerId < 0
        || nextLoanId < 0
        || books.size() > 10000
        || borrowers.size() > 10000
        || loans.size() > 10000)
      throw new IllegalArgumentException("Invalid revision, identity watermark or capacity.");
    var bookMap = new HashMap<Long, Book>();
    var people = new HashMap<Long, Borrower>();
    var loanIds = new HashSet<Long>();
    var emails = new HashSet<String>();
    var activeLoans = new HashMap<Long, Loan>();
    for (Book b : books)
      if (bookMap.put(b.getId(), b) != null || b.getId() >= nextBookId)
        throw new IllegalArgumentException("Duplicate book or invalid watermark.");
    for (Borrower b : borrowers) {
      if (people.put(b.getId(), b) != null || b.getId() >= nextBorrowerId)
        throw new IllegalArgumentException("Duplicate borrower or invalid watermark.");
      if (b.getEmail() != null && !emails.add(b.getEmail()))
        throw new IllegalArgumentException("Borrower email is already registered.");
    }
    for (Loan loan : loans) {
      if (!loanIds.add(loan.id())
          || loan.id() >= nextLoanId
          || !bookMap.containsKey(loan.bookId())
          || !people.containsKey(loan.borrowerId()))
        throw new IllegalArgumentException("Invalid loan identity or reference.");
      if (loan.returnedAt() == null && activeLoans.put(loan.bookId(), loan) != null)
        throw new IllegalArgumentException("Book has more than one active loan.");
    }
    for (Book book : books) {
      Loan loan = activeLoans.get(book.getId());
      if (book.isCheckedOut() != (loan != null))
        throw new IllegalArgumentException("Current book state and active loan disagree.");
      if (loan != null
          && (!Objects.equals(book.getBorrowerId(), loan.borrowerId())
              || !Objects.equals(book.getCheckedOutAt(), loan.checkedOutAt())
              || !Objects.equals(book.getDueDate(), loan.dueDate())
              || !people.get(loan.borrowerId()).isActive()))
        throw new IllegalArgumentException("Active loan fields or borrower state disagree.");
    }
  }

  public static LibraryData empty() {
    return new LibraryData(0, 0, 0, 0, List.of(), List.of(), List.of());
  }
}
