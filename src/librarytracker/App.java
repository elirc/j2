package librarytracker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import librarytracker.model.*;
import librarytracker.service.LibraryManager;
import librarytracker.storage.FileStorage;

public final class App {
  private final LibraryManager manager;
  private final Scanner scanner = new Scanner(System.in);

  public App(LibraryManager manager) {
    this.manager = manager;
  }

  public static void main(String[] args) {
    if (args.length > 1) {
      System.err.println("Usage: java -cp out librarytracker.App [data-directory]");
      System.exit(2);
    }
    try {
      new App(new LibraryManager(new FileStorage(Path.of(args.length == 1 ? args[0] : "data"))))
          .run();
    } catch (IOException | IllegalArgumentException e) {
      System.err.println(
          "Startup stopped; existing library files were not replaced. " + e.getMessage());
      System.exit(1);
    }
  }

  public void run() {
    System.out.println("Library Book Tracker / every accepted change is saved immediately.");
    try {
      while (true) {
        System.out.println(
            "\n"
                + "1 Add book | 2 List books | 3 Register borrower | 4 List borrowers | 5 Check out"
                + " | 6 Return | 7 Active loans | 8 Save status | 9 Exit");
        System.out.println(
            "10 Edit book | 11 Delete unused book | 12 Edit borrower | 13 Delete unused borrower |"
                + " 14 Search | 15 Reload | 16 Loan history | 17 Book archive/restore | 18 Borrower"
                + " archive/restore");
        System.out.print("Choose: ");
        if (!scanner.hasNextLine()) break;
        String choice = scanner.nextLine().strip();
        if (choice.equals("9")) break;
        try {
          switch (choice) {
            case "1" -> {
              Book b =
                  manager.addBook(line("Title: "), line("Author: "), line("ISBN (optional): "));
              System.out.println("Book added and saved with ID " + b.getId() + ".");
            }
            case "2" -> listBooks(manager.getAllBooks());
            case "3" -> {
              Borrower b =
                  manager.registerBorrower(
                      line("Name: "), line("Email (optional): "), line("Phone (optional): "));
              System.out.println("Borrower registered and saved with ID " + b.getId() + ".");
            }
            case "4" -> {
              if (manager.getAllBorrowers().isEmpty()) System.out.println("No borrowers.");
              for (Borrower b : manager.getAllBorrowers()) System.out.println(person(b));
            }
            case "5" -> {
              Book b = selectedBook();
              long person = id("Borrower ID: ");
              String date = line("Due date (YYYY-MM-DD, optional): ");
              Loan loan =
                  manager.checkOutBook(
                      b.getId(),
                      b.getVersion(),
                      person,
                      date.isEmpty() ? null : LocalDate.parse(date));
              System.out.println("Book checked out and saved. Loan " + loan.id() + ".");
            }
            case "6" -> {
              Book b = selectedBook();
              manager.returnBook(b.getId(), b.getVersion());
              System.out.println("Book returned and saved; loan history retained.");
            }
            case "7" -> listBooks(manager.getCheckedOutBooks());
            case "8" ->
                System.out.println(
                    "All accepted changes are already saved. Current snapshot revision "
                        + manager.revision()
                        + ".");
            case "10" -> {
              Book b = selectedBook();
              System.out.println(book(b));
              manager.editBook(
                  b.getId(),
                  b.getVersion(),
                  line("New title: "),
                  line("New author: "),
                  line("New ISBN (blank clears): "));
              System.out.println("Book edited and saved.");
            }
            case "11" -> {
              Book b = selectedBook();
              if (confirm("book " + b.getId())) {
                manager.deleteBook(b.getId(), b.getVersion());
                System.out.println("Book deleted and saved.");
              }
            }
            case "12" -> {
              Borrower b = selectedBorrower();
              System.out.println(person(b));
              manager.editBorrower(
                  b.getId(),
                  b.getVersion(),
                  line("New name: "),
                  line("New email (blank clears): "),
                  line("New phone (blank clears): "));
              System.out.println("Borrower edited and saved.");
            }
            case "13" -> {
              Borrower b = selectedBorrower();
              if (confirm("borrower " + b.getId())) {
                manager.deleteBorrower(b.getId(), b.getVersion());
                System.out.println("Borrower deleted and saved.");
              }
            }
            case "14" ->
                listBooks(
                    manager.searchBooks(
                        line("Search title/author/ISBN: "),
                        line("Status (all/available/out/archived): ")));
            case "15" -> {
              manager.reload();
              System.out.println(
                  "Current library loaded. Review it before repeating rejected changes.");
            }
            case "16" -> {
              if (manager.getLoanHistory().isEmpty()) System.out.println("No recorded loans.");
              for (Loan l : manager.getLoanHistory())
                System.out.println(
                    "Loan "
                        + l.id()
                        + " | Book "
                        + l.bookId()
                        + ": "
                        + l.bookTitle()
                        + " | Borrower "
                        + l.borrowerId()
                        + ": "
                        + l.borrowerName()
                        + " | Out "
                        + l.checkedOutAt()
                        + " | Due "
                        + Objects.toString(l.dueDate(), "none")
                        + " | Returned "
                        + Objects.toString(l.returnedAt(), "active"));
            }
            case "17" -> {
              Book b = selectedBook();
              if (line((b.isActive() ? "Archive" : "Restore") + " this book? Type YES: ")
                  .equals("YES")) {
                manager.setBookActive(b.getId(), b.getVersion(), !b.isActive());
                System.out.println("Book availability saved.");
              } else System.out.println("Change cancelled.");
            }
            case "18" -> {
              Borrower b = selectedBorrower();
              if (line((b.isActive() ? "Archive" : "Restore") + " this borrower? Type YES: ")
                  .equals("YES")) {
                manager.setBorrowerActive(b.getId(), b.getVersion(), !b.isActive());
                System.out.println("Borrower availability saved.");
              } else System.out.println("Change cancelled.");
            }
            default -> System.out.println("Choose a listed menu number.");
          }
        } catch (IOException
            | IllegalArgumentException
            | IllegalStateException
            | java.time.DateTimeException e) {
          System.out.println("Change not accepted: " + e.getMessage());
        }
      }
    } catch (NoSuchElementException e) {
      System.out.println("Input ended; incomplete command was not applied.");
    } finally {
      scanner.close();
      System.out.println("Goodbye. Previously accepted changes remain saved.");
    }
  }

  private String line(String prompt) {
    System.out.print(prompt);
    if (!scanner.hasNextLine()) throw new NoSuchElementException();
    return scanner.nextLine().strip();
  }

  private long id(String prompt) {
    String value = line(prompt);
    if (!value.matches("0|[1-9][0-9]*"))
      throw new IllegalArgumentException("Enter a nonnegative record ID.");
    return Long.parseLong(value);
  }

  private Book selectedBook() {
    return manager
        .findBookById(id("Book ID: "))
        .orElseThrow(() -> new IllegalArgumentException("Book not found."));
  }

  private Borrower selectedBorrower() {
    return manager
        .findBorrowerById(id("Borrower ID: "))
        .orElseThrow(() -> new IllegalArgumentException("Borrower not found."));
  }

  private boolean confirm(String target) {
    boolean yes = line("Delete unused " + target + " permanently? Type DELETE: ").equals("DELETE");
    if (!yes) System.out.println("Deletion cancelled.");
    return yes;
  }

  private String book(Book b) {
    return "ID "
        + b.getId()
        + " / v"
        + b.getVersion()
        + " | "
        + b.getTitle()
        + " | "
        + b.getAuthor()
        + " | "
        + (!b.isActive()
            ? "Archived"
            : b.isCheckedOut()
                ? "Checked out to "
                    + b.getBorrowerId()
                    + " / due "
                    + Objects.toString(b.getDueDate(), "none")
                    + (b.isOverdue(LocalDate.now()) ? " (OVERDUE)" : "")
                : "Available");
  }

  private String person(Borrower b) {
    return "ID "
        + b.getId()
        + " / v"
        + b.getVersion()
        + " | "
        + b.getName()
        + " | "
        + Objects.toString(b.getEmail(), "")
        + " | "
        + Objects.toString(b.getPhone(), "")
        + " | "
        + (b.isActive() ? "Active" : "Archived");
  }

  private void listBooks(List<Book> books) {
    if (books.isEmpty()) System.out.println("No matching books.");
    for (Book b : books) System.out.println(book(b));
  }
}
