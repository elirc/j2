# Historical original README

The source-linked learning course (folder `astraupskill`) and current root README supersede the original mutable models, separate-file writes, and save-on-exit examples below.

# Library Book Tracker

This is a small Java command-line project for tracking:

- books
- borrowers
- checkout and return status

It is intentionally small and beginner-friendly:

- plain Java only
- no frameworks
- simple OOP structure
- local file storage
- clear separation between UI, business logic, models, and persistence

## 1. Project Overview

The app starts, loads saved data from disk, and shows a text menu in a loop.

From the menu, a user can:

- add a book
- list all books
- register a borrower
- list all borrowers
- check out a book
- return a book
- view checked-out books
- save data manually
- exit the program

When the program exits, it saves automatically.

## 2. Project Structure

```text
src/
  librarytracker/
    App.java
    model/
      Book.java
      Borrower.java
    service/
      LibraryManager.java
    storage/
      FileStorage.java
```

The `data` folder is created automatically the first time the app saves or loads data.

## 3. How To Compile And Run

Use Java 17 or newer.

From the project root in PowerShell:

```powershell
javac -d out src\librarytracker\App.java src\librarytracker\model\Book.java src\librarytracker\model\Borrower.java src\librarytracker\service\LibraryManager.java src\librarytracker\storage\FileStorage.java
java -cp out librarytracker.App
```

## 4. File-By-File Explanation

### `src/librarytracker/App.java`

This is the command-line interface.

Responsibilities:

- start the program
- create the storage and manager objects
- show the menu
- read user input
- call the manager
- print results

### `src/librarytracker/model/Book.java`

This class represents one book.

It stores:

- book identity
- title and author
- optional ISBN
- whether it is checked out
- who borrowed it
- when it was checked out
- when it is due

### `src/librarytracker/model/Borrower.java`

This class represents one borrower.

It stores:

- borrower identity
- name
- optional email
- optional phone

### `src/librarytracker/service/LibraryManager.java`

This is the business logic layer.

It is responsible for:

- managing the in-memory lists of books and borrowers
- creating stable unique IDs
- checking whether a book or borrower exists
- enforcing checkout and return rules
- saving the current data through the storage class

### `src/librarytracker/storage/FileStorage.java`

This is the persistence layer.

It is responsible for:

- loading books from disk
- loading borrowers from disk
- saving books to disk
- saving borrowers to disk
- creating the `data` directory if needed

## 5. Class-By-Class Explanation

### `App`

Purpose:

- user interaction

Important idea:

- this class should ask questions and show results, not decide the library rules

### `Book`

Purpose:

- represent one book and its current checkout state

Important idea:

- the book object knows whether it is checked out and stores the borrower ID when it is borrowed

### `Borrower`

Purpose:

- represent one person who can borrow books

Important idea:

- this class is mostly a simple data container

### `LibraryManager`

Purpose:

- central place for business rules

Important idea:

- keeping the rules here prevents the UI from becoming messy

### `FileStorage`

Purpose:

- read and write text files

Important idea:

- file handling stays separate from the rest of the program

## 6. Method-By-Method Explanation

### `App.main`

- creates `FileStorage`
- creates `LibraryManager`
- creates `App`
- starts the menu loop

### `App.run`

- prints the welcome text
- repeatedly shows the menu
- sends each menu choice to the correct method

### `App.printMenu`

- prints the available menu options

### `App.addBook`

- asks the user for title, author, and optional ISBN
- sends that data to `LibraryManager.addBook`

### `App.listBooks`

- asks the manager for all books
- prints each book in a readable format

### `App.registerBorrower`

- asks the user for borrower information
- sends that data to `LibraryManager.registerBorrower`

### `App.listBorrowers`

- asks the manager for all borrowers
- prints them

### `App.checkOutBook`

- asks for a book ID
- asks for a borrower ID
- asks for an optional due date
- sends the data to `LibraryManager.checkOutBook`

### `App.returnBook`

- asks for a book ID
- sends the ID to `LibraryManager.returnBook`

### `App.viewCheckedOutBooks`

- asks the manager for only checked-out books
- prints them

### `App.saveData`

- tells the manager to save the current data

### `App.exitApplication`

- saves data
- prints a goodbye message

### `App.readInt`

- safely reads a whole number for the menu

### `App.readLong`

- safely reads a long number for IDs

### `App.readRequiredText`

- reads text until the user enters something non-empty

### `App.readOptionalText`

- reads text and allows blank input

### `App.readOptionalDate`

- reads a date in `YYYY-MM-DD` format
- returns `null` if blank

### `App.formatBook`

- creates the single-line display text for one book

### `App.buildBookStatus`

- creates the status text for a book
- shows whether the book is available, checked out, due, or overdue

### `App.formatBorrower`

- creates the single-line display text for one borrower

### `Book.Book(...)`

- there are two constructors
- one constructor is used for a new book
- one constructor is used when rebuilding a book from saved data

### `Book.getId`, `Book.getTitle`, `Book.getAuthor`, `Book.getIsbn`, `Book.isCheckedOut`, `Book.getBorrowerId`, `Book.getCheckedOutAt`, `Book.getDueDate`

- these methods return the book fields

### `Book.isOverdue`

- returns `true` when the book is checked out and the due date is before today

### `Book.checkOut`

- sets the borrower ID
- sets the checkout date
- sets the due date
- marks the book as checked out

### `Book.returnBook`

- clears borrower and checkout information
- marks the book as available again

### `Borrower.Borrower(...)`

- creates a borrower object

### `Borrower.getId`, `Borrower.getName`, `Borrower.getEmail`, `Borrower.getPhone`

- return the borrower fields

### `LibraryManager.LibraryManager(...)`

- loads saved data from the storage class
- copies that data into in-memory lists
- calculates the next available IDs

### `LibraryManager.addBook`

- creates a new `Book`
- gives it a stable unique ID
- adds it to the book list

### `LibraryManager.registerBorrower`

- creates a new `Borrower`
- gives it a stable unique ID
- adds it to the borrower list

### `LibraryManager.getAllBooks`

- returns all books sorted by ID

### `LibraryManager.getAllBorrowers`

- returns all borrowers sorted by ID

### `LibraryManager.getCheckedOutBooks`

- returns only books whose `checkedOut` value is `true`

### `LibraryManager.checkOutBook`

- checks whether the book exists
- checks whether the borrower exists
- checks whether the book is already checked out
- if valid, updates the book state

### `LibraryManager.returnBook`

- checks whether the book exists
- checks whether the book is currently checked out
- if valid, clears the checkout state

### `LibraryManager.findBookById`

- searches the list of books for a matching ID

### `LibraryManager.findBorrowerById`

- searches the list of borrowers for a matching ID

### `LibraryManager.findBorrowerForBook`

- looks up the borrower whose ID matches a checked-out book's `borrowerId`

### `LibraryManager.saveData`

- passes the current lists to the storage class

### `LibraryManager.findNextBookId`

- finds the highest current book ID and returns the next number

### `LibraryManager.findNextBorrowerId`

- finds the highest current borrower ID and returns the next number

### `LibraryManager.emptyToNull`

- converts blank optional text into `null`

### `FileStorage.FileStorage(...)`

- stores the folder path
- prepares the paths for `books.txt` and `borrowers.txt`

### `FileStorage.load`

- creates the data directory if needed
- loads both books and borrowers
- returns both lists inside `LibraryData`

### `FileStorage.save`

- creates the data directory if needed
- saves both books and borrowers

### `FileStorage.loadBooks`

- reads the books file
- converts each saved record into a `Book`

### `FileStorage.loadBorrowers`

- reads the borrowers file
- converts each saved record into a `Borrower`

### `FileStorage.saveBooks`

- writes each book as a block of `key=value` lines

### `FileStorage.saveBorrowers`

- writes each borrower as a block of `key=value` lines

### `FileStorage.readRecords`

- reads a file and groups lines into records
- uses `---` as the record separator

### `FileStorage.blankToNull`

- converts blank text into `null`

### `FileStorage.parseLongOrNull`

- converts text into `Long`
- returns `null` for blank values

### `FileStorage.parseDateOrNull`

- converts text into `LocalDate`
- returns `null` for blank values

### `FileStorage.valueOrBlank`

- converts a value to text
- returns an empty string when the value is `null`

### `FileStorage.clean`

- replaces line breaks with spaces so saved values stay one line each

### `FileStorage.LibraryData`

- small helper class used to return both book and borrower lists together

## 7. How Books And Borrowers Relate To Each Other

A `Book` does not store the full `Borrower` object.

Instead, it stores only:

- `borrowerId`

That means:

- the book keeps a reference to the borrower by ID
- the manager can look up the full borrower later
- this keeps the model simple and avoids tightly coupling the two classes

This is a very common beginner-friendly pattern:

- one object stores another object's ID
- a manager or service class connects them when needed

## 8. How Checkout And Return Flows Work

### Checkout flow

1. The user chooses the checkout menu option.
2. The app reads the book ID.
3. The app reads the borrower ID.
4. The app reads an optional due date.
5. `LibraryManager.checkOutBook` checks that the book exists.
6. It checks that the borrower exists.
7. It checks that the book is not already checked out.
8. If valid, the book is updated with borrower ID, checkout date, and due date.

### Return flow

1. The user chooses the return menu option.
2. The app reads the book ID.
3. `LibraryManager.returnBook` checks that the book exists.
4. It checks that the book is currently checked out.
5. If valid, `Book.returnBook` clears the checkout fields.

## 9. How Saving And Loading Work

### On startup

- `App.main` creates `FileStorage`
- `LibraryManager` calls `fileStorage.load()`
- if files exist, data is rebuilt into objects
- if files do not exist, empty lists are used

### On save

- `LibraryManager.saveData` calls `FileStorage.save`
- books are written to `data/books.txt`
- borrowers are written to `data/borrowers.txt`

### File format

The saved files use a simple record format:

```text
id=0
title=Clean Code
author=Robert C. Martin
isbn=9780132350884
checkedOut=true
borrowerId=0
checkedOutAt=2026-03-26
dueDate=2026-04-05
---
```

This format is easy to inspect by hand and easier for a beginner to understand than binary serialization.

## 10. Why The Project Was Structured This Way

This project uses a small number of classes with clear jobs:

- `App` handles input and output
- `Book` and `Borrower` hold domain data
- `LibraryManager` handles rules
- `FileStorage` handles saving and loading

This structure is useful because it avoids:

- one giant `main` method
- duplicated rule checks in many places
- mixing file code with menu code

It stays small enough for a beginner to read fully.

## 11. What A Beginner Should Study First

Study the project in this order:

1. `Book`
2. `Borrower`
3. `LibraryManager`
4. `App`
5. `FileStorage`

Why this order works:

- first understand the data
- then understand the business rules
- then understand how the user interacts with the rules
- finally understand persistence

Java topics worth studying while reading this project:

- classes and objects
- constructors
- fields and getters
- lists
- loops
- conditionals
- `Optional`
- `LocalDate`
- file reading and writing

## 12. Three Small Exercises To Extend The Project

1. Add a search option that finds books by title or author.

2. Add a default due date of 14 days when the user leaves the due date blank.

3. Show how many books each borrower currently has checked out.

## 13. Final Beginner Advice

Try reading the code in small passes instead of all at once:

1. Read the model classes.
2. Trace one feature, such as adding a book.
3. Trace one rule, such as checking out a book.
4. Trace saving and loading last.

This project is intentionally simple. The goal is not to build the most advanced library system. The goal is to build a version that teaches solid Java fundamentals without hiding the logic behind frameworks or advanced patterns.
