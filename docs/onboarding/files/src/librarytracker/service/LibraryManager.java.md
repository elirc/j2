# `src/librarytracker/service/LibraryManager.java`

## What This File Is For

This is the service layer and the main home for business rules.

In plain language:

- it owns the current in-memory library state
- it creates new books and borrowers
- it enforces checkout and return rules
- it connects books to borrowers by ID
- it saves data through the storage layer

File role:

- service
- domain coordinator
- rule enforcement layer

## Why This File Matters

This is the most important file in the repo for understanding behavior rather than just structure.

If you want to know:

- where IDs come from
- what makes an operation valid
- which state changes are allowed
- what other files should call when they need something meaningful done

this is the file to study.

## Key Exports / Main Units

### Constructor `LibraryManager(FileStorage fileStorage)`

- loads saved data immediately
- copies loaded lists into in-memory state
- calculates the next available IDs

### `addBook`

- creates a new `Book`
- assigns the next stable book ID

### `registerBorrower`

- creates a new `Borrower`
- assigns the next stable borrower ID

### Query methods

- `getAllBooks`
- `getAllBorrowers`
- `getCheckedOutBooks`
- `findBookById`
- `findBorrowerById`
- `findBorrowerForBook`

These methods expose state in a controlled way and help the UI stay simple.

### State-changing methods

- `checkOutBook`
- `returnBook`
- `saveData`

These methods are the main business operations.

### Internal helpers

- `findNextBookId`
- `findNextBorrowerId`
- `emptyToNull`

These methods support stable IDs and clean optional-field handling.

## How It Works

### Startup

When the manager is constructed, it immediately loads data through `FileStorage`. That means this class is not a passive container. It participates in startup behavior.

After loading, it computes the next IDs by scanning the loaded objects and finding the highest current ID.

### In-Memory Ownership

The manager stores:

- `List<Book> books`
- `List<Borrower> borrowers`

These lists are treated as the authoritative in-memory state during the program run.

### Checkout Path

`checkOutBook` follows a clear sequence:

1. find the book
2. fail if it does not exist
3. find the borrower
4. fail if it does not exist
5. fail if the book is already checked out
6. mutate the book through `book.checkOut(...)`

This is a good example of business-rule orchestration:

- validation first
- mutation second
- persistence later

### Return Path

`returnBook` mirrors the checkout pattern:

1. find the book
2. fail if it does not exist
3. fail if it is not checked out
4. reset state through `book.returnBook()`

The symmetry between checkout and return is worth noticing during review.

### Save Path

`saveData` hands the current lists to `FileStorage`. The manager does not know the file format; it only knows that storage persists the state.

## How It Connects To Other Files

Inbound relationships:

- `App` calls almost every public method here

Outbound relationships:

- creates and reads `Book` and `Borrower` objects
- depends on `FileStorage` for load/save

Important contracts:

- `Book.checkOut` and `Book.returnBook` must keep the book's internal state coherent
- `FileStorage.load()` must return lists that are safe to treat as current state
- public methods return status messages that the UI prints directly

## Things A Junior Dev Should Notice

### IDs Are Derived From Existing State, Not A Global Counter File

That is simple and works for this app, but it means ID behavior depends on the loaded data being trustworthy.

### The Manager Protects Rules By Owning The Main Mutation Paths

If another file starts mutating books directly, this protection weakens quickly.

### Some Invariants Are Implicit

For example, the code assumes:

- if `checkedOut` is `true`, checkout-related fields make sense
- if `checkedOut` is `false`, checkout-related fields are cleared

Those assumptions are mostly maintained by the code, but not fully re-validated after loading.

### Public Methods Return Strings

That is convenient for a CLI app, but it couples the service layer a little bit to presentation concerns.

In a larger app, reviewers might ask whether a richer result type would age better.

## Code Review Lens

If this file appears in a PR, focus on:

- whether new behavior preserves invariants around checkout state
- whether success and failure paths are both covered
- whether a new rule belongs here instead of in the UI or model
- whether changes to ID handling could break saved-data continuity

Strong review questions:

- what state should be unchanged if this operation fails
- does this add a new rule in both the forward and reverse path
- what assumptions does this make about loaded data

## Suggested Exercises

1. Add a method that returns all books currently checked out by one borrower.
2. Change checkout so a blank due date becomes `LocalDate.now().plusDays(14)`.
3. Refactor status-message returns into a small result object and compare the tradeoffs.

## Observed Facts vs Inference

Observed from code:

- this file loads data during construction
- it owns book and borrower collections in memory
- it enforces core checkout and return rules
- it computes next IDs by scanning existing records

Inference / likely intent:

- this file is intended to be the central "one place to look" for library rules
- the author chose simple return strings because the app is CLI-only and beginner-focused
