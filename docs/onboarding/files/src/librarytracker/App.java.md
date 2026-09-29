# `src/librarytracker/App.java`

## What This File Is For

This is the application entry point and the command-line UI layer.

In plain language:

- it starts the program
- wires together storage and business logic
- shows the menu
- reads user input
- prints results back to the terminal

File role:

- entry point
- CLI controller
- presentation layer

## Why This File Matters

If you want to understand how a user experiences the program, this is the file to study first after the models and service layer.

It is also a good example of a healthy boundary:

- it coordinates actions
- it does not own the checkout/return rules
- it delegates real operations to `LibraryManager`

That makes this file useful for learning where UI logic should stop.

## Key Exports / Main Units

### `main(String[] args)`

- process entry point
- creates `FileStorage`, `LibraryManager`, and `App`
- handles startup `IOException`

### `run()`

- owns the main menu loop
- dispatches each menu choice to a helper method

### Action methods

- `addBook()`
- `listBooks()`
- `registerBorrower()`
- `listBorrowers()`
- `checkOutBook()`
- `returnBook()`
- `viewCheckedOutBooks()`
- `saveData()`
- `exitApplication()`

These methods are the main bridges from user intent to service-layer behavior.

### Input helpers

- `readInt`
- `readLong`
- `readRequiredText`
- `readOptionalText`
- `readOptionalDate`

These methods keep input validation localized instead of duplicating it in every menu action.

### Formatting helpers

- `formatBook`
- `buildBookStatus`
- `formatBorrower`

These methods turn domain objects into readable terminal output.

## How It Works

The file has three main jobs.

### 1. Startup Wiring

`main` builds the dependency chain:

1. create `FileStorage`
2. create `LibraryManager`
3. create `App`
4. call `run`

Notice that startup loading happens indirectly when `LibraryManager` is created.

### 2. Menu-Driven Control Flow

`run` loops until the user chooses exit. Each menu option calls one helper method. That keeps the loop readable and makes each action easier to inspect in isolation.

The important architectural point is that these action methods mostly:

- gather input
- call one manager method
- print the result

That pattern is simple, but it is exactly what keeps the UI from swallowing the business logic.

### 3. Display Logic

This file also decides how domain data is shown to the user.

The most interesting display path is `buildBookStatus`, which:

- shows "Available" when a book is not checked out
- resolves the borrower name through `libraryManager.findBorrowerForBook`
- shows checkout and due dates if present
- marks overdue books using `book.isOverdue(LocalDate.now())`

That means this file contains presentation logic, not just input logic.

## How It Connects To Other Files

Inbound relationships:

- the JVM calls `main`

Outbound relationships:

- constructs `FileStorage`
- constructs `LibraryManager`
- reads and formats `Book` and `Borrower`
- relies on `LibraryManager` for every meaningful state change

Important contracts:

- `LibraryManager` returns user-facing status messages like "Book not found."
- `Book.isCheckedOut`, `Book.getDueDate`, and `Book.isOverdue` shape status rendering
- `findBorrowerForBook` must return the borrower linked by `borrowerId` if the data is consistent

## Things A Junior Dev Should Notice

### The UI Does Validation, But Only Boundary Validation

This file validates user input shape, such as making sure menu options are numbers and required text is not blank.

It does not validate business rules like:

- whether a book exists
- whether a borrower exists
- whether a book is already checked out

That split is good design.

### `App` Is Doing Some Presentation Reasoning

`buildBookStatus` decides how overdue state appears. That is fine in a small app, but it means some date-related behavior lives outside the service layer.

If overdue rules become more complex, that logic may deserve a more central home.

### Save Happens On Demand, Not After Every Mutation

The UI triggers persistence explicitly through:

- menu option 8
- exit path

That is a deliberate tradeoff between simplicity and durability.

## Code Review Lens

If this file shows up in a PR, focus on:

- whether the change is still orchestration and presentation, not hidden business logic
- whether input retries and error messages are clear
- whether a new menu option cleanly maps to a service call
- whether formatting code assumes domain states that may not always hold

Strong review questions:

- should this rule move into `LibraryManager`
- what happens if the user enters invalid input repeatedly
- if save fails, what exactly does the user see

## Suggested Exercises

1. Add a new menu option to search books by title, but keep the actual search rule out of `App`.
2. Change the status display so books with no due date show `No due date set`.
3. Trace what happens if startup loading fails and explain what the user will experience.

## Observed Facts vs Inference

Observed from code:

- this file is the process entry point
- it owns the menu loop and terminal input/output
- it delegates state-changing operations to `LibraryManager`
- it computes overdue display text using current date at display time

Inference / likely intent:

- the author wanted `App` to stay simple and beginner-readable rather than abstract
- the split between input validation and business validation is intentional, even if not explicitly documented in code
