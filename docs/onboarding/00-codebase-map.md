# Codebase Map

## What Kind Of Application This Is

Observed from code:

- This repo is a plain Java command-line application for tracking library books, borrowers, and checkout status.
- The runtime starts in `src/librarytracker/App.java`.
- Data is persisted to local text files under a `data/` directory through `src/librarytracker/storage/FileStorage.java`.

Inference / likely intent:

- The project is designed as a teaching-sized app. It favors explicit control flow and small classes over flexibility or advanced abstractions.
- The main audience appears to be learners or developers who benefit from seeing boundaries directly in the code instead of through frameworks.

## Main Architectural Layers

### 1. CLI / Presentation Layer

Owned by:

- `src/librarytracker/App.java`

What it does:

- owns the menu loop
- reads user input with `Scanner`
- prints success and error messages
- formats books and borrowers for display

Why it exists:

- to keep user interaction concerns separate from business rules and file I/O

### 2. Domain Model Layer

Owned by:

- `src/librarytracker/model/Book.java`
- `src/librarytracker/model/Borrower.java`

What it does:

- represents the core entities in memory
- stores persistent fields like IDs, names, status, and dates
- exposes a small amount of behavior directly on the model, such as `Book.checkOut`, `Book.returnBook`, and `Book.isOverdue`

Why it exists:

- to give the rest of the program a stable vocabulary for "what a book is" and "what a borrower is"

### 3. Service / Business Logic Layer

Owned by:

- `src/librarytracker/service/LibraryManager.java`

What it does:

- manages the in-memory collections of books and borrowers
- enforces core rules like "a checked-out book cannot be checked out again"
- looks up relationships between books and borrowers
- assigns stable IDs
- delegates persistence to storage

Why it exists:

- to centralize rules that should not be spread across the CLI or storage code

### 4. Persistence / Filesystem Boundary

Owned by:

- `src/librarytracker/storage/FileStorage.java`

What it does:

- creates the `data/` directory if needed
- reads `books.txt` and `borrowers.txt`
- converts file records into model objects
- writes model objects back to disk

Why it exists:

- to isolate file format and filesystem concerns from business logic

## Major Runtime Boundaries

### User Input / Output Boundary

Observed from code:

- `App` reads text input from `System.in` and writes output with `System.out.println`.

Why this boundary matters:

- bugs here often look like validation problems, menu-flow mistakes, or unclear user feedback rather than business-rule bugs

### Business Rule Boundary

Observed from code:

- `LibraryManager` sits between the CLI and the models/storage.
- `App` asks the manager to perform actions rather than mutating `Book` or `Borrower` lists directly.

Why this boundary matters:

- if a change bypasses `LibraryManager`, rules can silently drift or be duplicated

### Filesystem Boundary

Observed from code:

- `FileStorage` is the only class that reads from or writes to disk.

Why this boundary matters:

- persistence bugs can corrupt future runs, not just the current one

### Time Boundary

Observed from code:

- checkout uses `LocalDate.now()` in `LibraryManager`
- overdue display uses `LocalDate.now()` in `App`

Why this boundary matters:

- anything date-related can be surprisingly brittle in tests and during future feature work

## Important Directories And What They Own

### `src/librarytracker/`

- top-level application package
- owns the main entry point and menu orchestration

### `src/librarytracker/model/`

- core data types
- should stay simple and focused on domain state

### `src/librarytracker/service/`

- central business logic
- best place for rule enforcement and cross-entity coordination

### `src/librarytracker/storage/`

- persistence logic
- owns file format details and model reconstruction

### `docs/onboarding/`

- onboarding docs for future contributors
- should explain the architecture, not just mirror code comments

### `data/`

Observed from code:

- created at runtime if missing
- stores `books.txt` and `borrowers.txt`

Inference / likely intent:

- this directory is part of the app's durable state, even though it is not source code

## How Data Flows Through The App

### Add Book Flow

1. `App.addBook` collects title, author, and optional ISBN from the user.
2. `App` calls `LibraryManager.addBook`.
3. `LibraryManager` creates a `Book` with the next stable ID and stores it in memory.
4. The data stays only in memory until the user chooses save or exits.
5. `LibraryManager.saveData` passes the in-memory lists to `FileStorage.save`.
6. `FileStorage.saveBooks` serializes each book to `data/books.txt`.

### Checkout Flow

1. `App.checkOutBook` reads the book ID, borrower ID, and optional due date.
2. `LibraryManager.checkOutBook` verifies the book exists.
3. `LibraryManager.checkOutBook` verifies the borrower exists.
4. `LibraryManager.checkOutBook` verifies the book is not already checked out.
5. `Book.checkOut` updates the book's checkout-related fields.
6. Later, `FileStorage.saveBooks` writes those updated fields to disk.

### Startup Load Flow

1. `App.main` creates `FileStorage`.
2. `App.main` creates `LibraryManager`.
3. `LibraryManager` immediately calls `fileStorage.load()`.
4. `FileStorage` reads `books.txt` and `borrowers.txt` if they exist.
5. `FileStorage` reconstructs `Book` and `Borrower` objects.
6. `LibraryManager` calculates the next IDs from the loaded data.

## Key Concepts And Invariants

### IDs Are The Stable Link Between Records

Observed from code:

- `Book` stores `borrowerId`, not a `Borrower` object.
- `LibraryManager.findBorrowerForBook` resolves that relationship when needed.

Why it matters:

- if IDs are changed carelessly, existing saved data may no longer link correctly

### Checkout State Is A Small State Machine

Observed from code:

- available book: `checkedOut == false`, related checkout fields should be `null`
- checked-out book: `checkedOut == true`, borrower/date fields may be populated
- `Book.returnBook` clears borrower and due-date-related fields

Inference / likely intent:

- the code expects these fields to stay consistent as a group, even though there is no explicit validator enforcing every combination

### Storage Format Is Simple But Fragile

Observed from code:

- `FileStorage` uses plain `key=value` records separated by `---`
- missing files are treated as empty data

Why it matters:

- the format is easy to inspect by hand, but manual edits or malformed values can break loading

### The Service Layer Owns The Rules

Observed from code:

- `App` does not directly mutate lists or call `Book.checkOut` on its own

Why it matters:

- future features should usually preserve that pattern

## Where To Be Careful

### Partial Or Malformed File Data

Observed from code:

- `FileStorage` parses strings into `long` and `LocalDate` without defensive error recovery

Risk:

- a malformed saved file can fail the whole startup path

### Inconsistent Checkout Fields

Observed from code:

- `LibraryManager` checks `book.isCheckedOut()` before allowing return or checkout
- it does not explicitly validate that `borrowerId`, `checkedOutAt`, and `dueDate` are consistent with that flag on load

Risk:

- corrupted or hand-edited data could produce states the UI was not designed for

### Time Logic Split Across Layers

Observed from code:

- checkout date is assigned in `LibraryManager`
- overdue rendering is decided in `App`

Risk:

- if overdue behavior becomes more important, logic may need to move to the service or model layer to avoid duplication

### Save Semantics

Observed from code:

- changes happen in memory immediately
- persistence happens only on explicit save or exit

Risk:

- a crash before save loses recent changes

Inference / likely intent:

- that tradeoff was accepted to keep the app simpler for learners
