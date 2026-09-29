package librarytracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import librarytracker.model.*;
import librarytracker.service.LibraryManager;
import librarytracker.storage.*;

public final class LibraryWorkflowTest {
  private static int passed;
  private static Path root;
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

  @FunctionalInterface
  interface Checked {
    void run() throws Exception;
  }

  static void check(boolean value) {
    if (!value) throw new AssertionError("Assertion failed");
  }

  static void rejects(Class<? extends Throwable> type, Checked work) throws Exception {
    try {
      work.run();
    } catch (Throwable e) {
      if (type.isInstance(e)) return;
      throw new AssertionError("Expected " + type + " but got " + e, e);
    }
    throw new AssertionError("Expected rejection: " + type);
  }

  static void test(String name, Checked work) throws Exception {
    work.run();
    passed++;
    System.out.println("PASS " + name);
  }

  static Path directory() throws IOException {
    return Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
  }

  static LibraryManager manager(Path p) throws IOException {
    return new LibraryManager(new FileStorage(p), CLOCK);
  }

  static LibraryManager populated(Path p) throws IOException {
    LibraryManager m = manager(p);
    m.addBook("Reliable systems", "A. Author", "Example-ISBN");
    m.registerBorrower("Sam Reader", "Sam@Example.Test", "");
    return m;
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  static void legacy(Path p, boolean checkedOut) throws IOException {
    Files.writeString(
        p.resolve("books.txt"),
        "id=0\ntitle=Legacy book\nauthor=Legacy author\nisbn=\ncheckedOut="
            + checkedOut
            + "\nborrowerId="
            + (checkedOut ? "0" : "")
            + "\ncheckedOutAt="
            + (checkedOut ? "2026-09-01" : "")
            + "\ndueDate="
            + (checkedOut ? "2026-09-15" : "")
            + "\n---\n");
    Files.writeString(
        p.resolve("borrowers.txt"),
        "id=0\nname=Legacy reader\nemail=reader@example.test\nphone=\n---\n");
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("worker")) {
      LibraryManager m = manager(Path.of(args[1]));
      Files.writeString(Path.of(args[2]), "ready");
      long deadline = System.nanoTime() + 60_000_000_000L;
      while (!Files.exists(Path.of(args[3]))) {
        if (System.nanoTime() > deadline) throw new IOException("Barrier timeout");
        Thread.sleep(10);
      }
      try {
        m.addBook(args[4], "Concurrent author", "");
        System.out.println("ACCEPTED");
      } catch (IOException e) {
        System.out.println("CONFLICT");
      }
      return;
    }
    root = Path.of(args[0]);
    Files.createDirectories(root);
    test(
        "book validates required text and identity",
        () -> {
          rejects(IllegalArgumentException.class, () -> new Book(-1, "Title", "Author", ""));
          rejects(IllegalArgumentException.class, () -> new Book(0, " ", "Author", ""));
          rejects(IllegalArgumentException.class, () -> new Book(0, "Title", "A\nB", ""));
        });
    test(
        "borrower canonicalizes optional contact fields",
        () -> {
          Borrower b = new Borrower(0, " Sam ", " SAM@Example.Test ", " ");
          check(
              b.getName().equals("Sam")
                  && b.getEmail().equals("sam@example.test")
                  && b.getPhone() == null);
          rejects(IllegalArgumentException.class, () -> new Borrower(0, "Sam", "not-an-email", ""));
        });
    test(
        "book edits are immutable and versioned",
        () -> {
          Book old = new Book(0, "Title", "Author", "");
          Book next = old.edit("Updated", "Author", "");
          check(old.getTitle().equals("Title") && next.getVersion() == 2);
        });
    test(
        "borrower edits are immutable and versioned",
        () -> {
          Borrower old = new Borrower(0, "Reader", "", "");
          Borrower next = old.edit("Updated reader", "", "");
          check(old.getName().equals("Reader") && next.getVersion() == 2);
        });
    test(
        "checkout fields must be internally consistent",
        () -> {
          rejects(
              IllegalArgumentException.class,
              () -> new Book(0, "Title", "Author", "", true, null, TODAY, null));
          rejects(
              IllegalArgumentException.class,
              () -> new Book(0, "Title", "Author", "", false, 0L, TODAY, null));
        });
    test(
        "due date cannot precede checkout",
        () ->
            rejects(
                IllegalArgumentException.class,
                () -> new Book(0, "Title", "Author", "").checkOut(0, TODAY, TODAY.minusDays(1))));
    test(
        "date range is bounded",
        () ->
            rejects(
                IllegalArgumentException.class,
                () ->
                    new Book(0, "Title", "Author", "")
                        .checkOut(0, LocalDate.of(1800, 1, 1), null)));
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
    test(
        "overdue compares calendar dates",
        () -> {
          Book b = new Book(0, "Title", "Author", "").checkOut(0, TODAY, TODAY);
          check(!b.isOverdue(TODAY) && b.isOverdue(TODAY.plusDays(1)));
        });
    test(
        "archived or already checked-out books cannot be lent",
        () -> {
          rejects(
              IllegalStateException.class,
              () -> new Book(0, "Title", "Author", "").setActive(false).checkOut(0, TODAY, null));
          rejects(
              IllegalStateException.class,
              () ->
                  new Book(0, "Title", "Author", "")
                      .checkOut(0, TODAY, null)
                      .checkOut(0, TODAY, null));
        });
    test(
        "record version exhaustion rejects",
        () ->
            rejects(
                IllegalStateException.class,
                () ->
                    new Borrower(0, Integer.MAX_VALUE, "Reader", null, null, true)
                        .edit("Other", null, null)));
    test(
        "missing workspace loads without creating files",
        () -> {
          Path p = root.resolve("absent");
          check(manager(p).getAllBooks().isEmpty() && !Files.exists(p));
        });
    test(
        "all accepted creates survive restart",
        () -> {
          Path p = directory();
          populated(p);
          LibraryManager read = manager(p);
          check(
              read.getAllBooks().size() == 1
                  && read.getAllBorrowers().size() == 1
                  && read.revision() == 2);
        });
    test(
        "normalized nonempty borrower emails are unique",
        () -> {
          LibraryManager m = populated(directory());
          rejects(
              IllegalArgumentException.class,
              () -> m.registerBorrower("Other", "SAM@EXAMPLE.TEST", ""));
          check(m.getAllBorrowers().size() == 1);
        });
    test(
        "blank optional emails may repeat",
        () -> {
          LibraryManager m = manager(directory());
          m.registerBorrower("First", "", "");
          m.registerBorrower("Second", null, null);
          check(m.getAllBorrowers().size() == 2);
        });
    test(
        "book and borrower edits persist with stale-version rejection",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          m.editBook(0, 1, "Accepted title", "Author", "");
          m.editBorrower(0, 1, "Accepted reader", "new@example.test", "");
          rejects(IllegalStateException.class, () -> m.editBook(0, 1, "Stale", "Author", ""));
          rejects(IllegalStateException.class, () -> m.editBorrower(0, 1, "Stale", "", ""));
          check(manager(p).findBookById(0).orElseThrow().getTitle().equals("Accepted title"));
        });
    test(
        "unused records delete without ID reuse after restart",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          m.deleteBook(0, 1);
          m.deleteBorrower(0, 1);
          m = manager(p);
          check(
              m.addBook("Second", "Author", "").getId() == 1
                  && m.registerBorrower("Second", "", "").getId() == 1);
        });
    test(
        "checkout creates one current loan and retained receipt",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          Loan loan = m.checkOutBook(0, 1, 0, TODAY.plusDays(7));
          LibraryManager read = manager(p);
          check(
              read.getCheckedOutBooks().size() == 1
                  && read.getLoanHistory().size() == 1
                  && loan.bookTitle().equals("Reliable systems"));
        });
    test(
        "return retains accepted title and borrower name",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          m.checkOutBook(0, 1, 0, null);
          m.editBook(0, 2, "New title", "Author", "");
          m.editBorrower(0, 1, "Renamed reader", "sam@example.test", "");
          m.returnBook(0, 3);
          Loan l = manager(p).getLoanHistory().get(0);
          check(
              l.returnedAt().equals(TODAY)
                  && l.bookTitle().equals("Reliable systems")
                  && l.borrowerName().equals("Sam Reader"));
        });
    test(
        "double checkout and double return reject",
        () -> {
          LibraryManager m = populated(directory());
          m.checkOutBook(0, 1, 0, null);
          rejects(IllegalStateException.class, () -> m.checkOutBook(0, 2, 0, null));
          m.returnBook(0, 2);
          rejects(IllegalStateException.class, () -> m.returnBook(0, 3));
          check(m.getLoanHistory().size() == 1);
        });
    test(
        "missing borrower cannot create a partial checkout",
        () -> {
          LibraryManager m = populated(directory());
          rejects(IllegalArgumentException.class, () -> m.checkOutBook(0, 1, 99, null));
          check(m.getCheckedOutBooks().isEmpty() && m.getLoanHistory().isEmpty());
        });
    test(
        "archived borrower cannot borrow",
        () -> {
          LibraryManager m = populated(directory());
          m.setBorrowerActive(0, 1, false);
          rejects(IllegalStateException.class, () -> m.checkOutBook(0, 1, 0, null));
        });
    test(
        "active loans block book and borrower archival",
        () -> {
          LibraryManager m = populated(directory());
          m.checkOutBook(0, 1, 0, null);
          rejects(IllegalStateException.class, () -> m.setBookActive(0, 2, false));
          rejects(IllegalStateException.class, () -> m.setBorrowerActive(0, 1, false));
        });
    test(
        "returned history blocks deletion but permits archival and restore",
        () -> {
          LibraryManager m = populated(directory());
          m.checkOutBook(0, 1, 0, null);
          m.returnBook(0, 2);
          rejects(IllegalStateException.class, () -> m.deleteBook(0, 3));
          rejects(IllegalStateException.class, () -> m.deleteBorrower(0, 1));
          m.setBookActive(0, 3, false);
          m.setBorrowerActive(0, 1, false);
          m.setBookActive(0, 4, true);
          m.setBorrowerActive(0, 2, true);
          check(m.getLoanHistory().size() == 1);
        });
    test(
        "search is literal and status-aware",
        () -> {
          LibraryManager m = populated(directory());
          m.addBook("100% useful", "Another author", "");
          m.setBookActive(1, 1, false);
          check(
              m.searchBooks("%", "archived").size() == 1
                  && m.searchBooks("AUTHOR", "available").size() == 1);
        });
    test(
        "returned collections cannot mutate accepted state",
        () -> {
          LibraryManager m = populated(directory());
          rejects(UnsupportedOperationException.class, () -> m.getAllBooks().clear());
          rejects(UnsupportedOperationException.class, () -> m.getAllBorrowers().clear());
        });
    test(
        "snapshot roundtrip preserves Unicode and delimiters",
        () -> {
          Book b = new Book(0, "Text = | \u65e5\u672c", "Author", "");
          LibraryData d = new LibraryData(1, 1, 0, 0, List.of(b), List.of(), List.of());
          check(
              FileStorage.decode(FileStorage.encode(d))
                  .books()
                  .get(0)
                  .getTitle()
                  .equals(b.getTitle()));
        });
    test(
        "snapshot rejects missing active loan references",
        () -> {
          Book b = new Book(0, "Title", "Author", "").checkOut(0, TODAY, null);
          Borrower p = new Borrower(0, "Reader", "", "");
          rejects(
              IllegalArgumentException.class,
              () -> new LibraryData(1, 1, 1, 0, List.of(b), List.of(p), List.of()));
        });
    test(
        "snapshot rejects duplicate identities and invalid watermarks",
        () -> {
          Book b = new Book(0, "Title", "Author", "");
          rejects(
              IllegalArgumentException.class,
              () -> new LibraryData(1, 1, 0, 0, List.of(b, b), List.of(), List.of()));
          rejects(
              IllegalArgumentException.class,
              () -> new LibraryData(1, 0, 0, 0, List.of(b), List.of(), List.of()));
        });
    test(
        "snapshot rejects duplicate active loans",
        () -> {
          Book b = new Book(0, "Title", "Author", "").checkOut(0, TODAY, null);
          Borrower p = new Borrower(0, "Reader", "", "");
          Loan a = new Loan(0, 0, 0, "Title", "Reader", TODAY, null, null),
              c = new Loan(1, 0, 0, "Title", "Reader", TODAY, null, null);
          rejects(
              IllegalArgumentException.class,
              () -> new LibraryData(1, 1, 1, 2, List.of(b), List.of(p), List.of(a, c)));
        });
    test(
        "snapshot rejects truncated trailer and invalid counts",
        () -> {
          byte[] raw = FileStorage.encode(LibraryData.empty());
          rejects(IOException.class, () -> FileStorage.decode(Arrays.copyOf(raw, raw.length - 1)));
          rejects(
              IOException.class,
              () ->
                  FileStorage.decode(
                      bytes(
                          new String(raw, StandardCharsets.UTF_8)
                              .replace("END\t0\t0\t0", "END\t1\t0\t0"))));
        });
    test(
        "snapshot rejects malformed UTF8 and oversize input",
        () -> {
          rejects(IOException.class, () -> FileStorage.decode(new byte[] {(byte) 0xc3, 0x28}));
          rejects(IOException.class, () -> FileStorage.decode(new byte[FileStorage.MAX_BYTES + 1]));
        });
    test(
        "strict legacy pair converts without changing either source",
        () -> {
          Path p = directory();
          legacy(p, true);
          byte[] beforeBooks = Files.readAllBytes(p.resolve("books.txt")),
              beforePeople = Files.readAllBytes(p.resolve("borrowers.txt"));
          LibraryManager m = manager(p);
          check(m.getLoanHistory().size() == 1);
          m.returnBook(0, 1);
          check(
              Arrays.equals(beforeBooks, Files.readAllBytes(p.resolve("books.txt")))
                  && Arrays.equals(beforePeople, Files.readAllBytes(p.resolve("borrowers.txt")))
                  && Files.exists(p.resolve("library.snapshot")));
        });
    test(
        "one missing legacy file is a controlled error",
        () -> {
          Path p = directory();
          Files.writeString(p.resolve("books.txt"), "");
          rejects(IOException.class, () -> manager(p));
          check(!Files.exists(p.resolve("library.snapshot")));
        });
    test(
        "legacy missing borrower reference rejects entire load",
        () -> {
          Path p = directory();
          legacy(p, true);
          Files.writeString(p.resolve("borrowers.txt"), "");
          rejects(IOException.class, () -> manager(p));
        });
    test(
        "legacy duplicate and unknown fields reject",
        () -> {
          Path p = directory();
          legacy(p, false);
          String raw = Files.readString(p.resolve("books.txt"));
          Files.writeString(p.resolve("books.txt"), raw.replace("---", "id=9\n---"));
          rejects(IOException.class, () -> manager(p));
          Files.writeString(p.resolve("books.txt"), raw.replace("---", "extra=value\n---"));
          rejects(IOException.class, () -> manager(p));
        });
    test(
        "legacy booleans and dates are strict",
        () -> {
          Path p = directory();
          legacy(p, true);
          String raw = Files.readString(p.resolve("books.txt"));
          Files.writeString(
              p.resolve("books.txt"), raw.replace("checkedOut=true", "checkedOut=maybe"));
          rejects(IOException.class, () -> manager(p));
          Files.writeString(p.resolve("books.txt"), raw.replace("2026-09-01", "bad-date"));
          rejects(IOException.class, () -> manager(p));
        });
    test(
        "legacy truncation rejects",
        () -> {
          Path p = directory();
          legacy(p, false);
          Files.writeString(
              p.resolve("books.txt"),
              Files.readString(p.resolve("books.txt")).replace("---\n", ""));
          rejects(IOException.class, () -> manager(p));
        });
    test(
        "separate sessions reject stale files then recover by reload",
        () -> {
          Path p = directory();
          LibraryManager a = manager(p), b = manager(p);
          a.addBook("First", "Author", "");
          rejects(IOException.class, () -> b.addBook("Stale", "Author", ""));
          check(b.getAllBooks().isEmpty());
          b.reload();
          b.addBook("Second", "Author", "");
          check(manager(p).getAllBooks().size() == 2);
        });
    test(
        "legacy changes between load and save cannot be overwritten",
        () -> {
          Path p = directory();
          legacy(p, false);
          LibraryManager m = manager(p);
          Files.writeString(p.resolve("borrowers.txt"), "changed");
          rejects(IOException.class, () -> m.addBook("New", "Author", ""));
          check(!Files.exists(p.resolve("library.snapshot")));
        });
    test(
        "failed atomic checkout retains book state and no loan receipt",
        () -> {
          Path p = directory();
          populated(p);
          byte[] before = Files.readAllBytes(p.resolve("library.snapshot"));
          FileStorage failing =
              new FileStorage(p) {
                protected void replaceAtomically(Path a, Path b) throws IOException {
                  throw new IOException("Injected failure");
                }
              };
          LibraryManager m = new LibraryManager(failing, CLOCK);
          rejects(IOException.class, () -> m.checkOutBook(0, 1, 0, null));
          check(
              m.getCheckedOutBooks().isEmpty()
                  && m.getLoanHistory().isEmpty()
                  && Arrays.equals(before, Files.readAllBytes(p.resolve("library.snapshot"))));
        });
    test(
        "failed reload retains previous accepted values",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          Files.writeString(p.resolve("library.snapshot"), "bad");
          rejects(IOException.class, m::reload);
          check(m.getAllBooks().size() == 1);
          rejects(IOException.class, () -> m.addBook("Rejected", "Author", ""));
        });
    test(
        "invalid creates do not allocate identities",
        () -> {
          LibraryManager m = manager(directory());
          rejects(IllegalArgumentException.class, () -> m.addBook(" ", "Author", ""));
          check(m.addBook("Accepted", "Author", "").getId() == 0);
        });
    test(
        "loan ids advance across return and restart",
        () -> {
          Path p = directory();
          LibraryManager m = populated(p);
          m.checkOutBook(0, 1, 0, null);
          m.returnBook(0, 2);
          check(manager(p).checkOutBook(0, 3, 0, null).id() == 1);
        });
    System.out.println("RESULT {\"passed\":true,\"tests\":" + passed + "}");
  }
}
