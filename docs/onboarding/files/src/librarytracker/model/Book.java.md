# `src/librarytracker/model/Book.java`

## What This File Is For

This file defines the `Book` domain model.

In plain language:

- it represents one library book
- it stores both basic metadata and checkout-related state
- it provides a few small behaviors that update that state

File role:

- domain model
- core entity

## Why This File Matters

This is one of the two most important "noun" files in the system, and it carries more domain behavior than `Borrower`.

If you do not understand this file, you will struggle to reason about:

- what "checked out" really means in this system
- which fields must move together
- what the storage layer needs to persist

## Key Exports / Main Units

### Constructors

- `Book(long id, String title, String author, String isbn)`
- full constructor with checkout-related fields

Why there are two:

- one is for newly created books
- one is for reconstructing books from saved data

### Getter methods

- `getId`
- `getTitle`
- `getAuthor`
- `getIsbn`
- `isCheckedOut`
- `getBorrowerId`
- `getCheckedOutAt`
- `getDueDate`

These expose the stored state without direct setters.

### `isOverdue(LocalDate today)`

- checks whether the book is overdue relative to a supplied date

### `checkOut(long borrowerId, LocalDate checkedOutAt, LocalDate dueDate)`

- marks the book as checked out and stores the checkout metadata

### `returnBook()`

- clears checkout-related fields and restores the available state

## How It Works

The important idea is that this class stores a small state machine using ordinary fields.

### Available State

Typical available-state shape:

- `checkedOut == false`
- `borrowerId == null`
- `checkedOutAt == null`
- `dueDate == null`

### Checked-Out State

Typical checked-out-state shape:

- `checkedOut == true`
- `borrowerId` has a value
- `checkedOutAt` may have a value
- `dueDate` may have a value

### State Transitions

`checkOut(...)` moves the object into the checked-out state by setting:

- the boolean flag
- the borrower link
- checkout date
- due date

`returnBook()` resets those fields so the object reads as available again.

`isOverdue(...)` does not change state. It derives status from current fields and the supplied date.

## How It Connects To Other Files

Inbound relationships:

- `LibraryManager` creates books and mutates them through `checkOut` and `returnBook`
- `FileStorage` reconstructs books from persisted fields
- `App` reads book state for display

Outbound relationships:

- uses `LocalDate` for checkout-related dates

Important contracts:

- `returnBook()` should clear all checkout-related fields, not just the boolean flag
- `isOverdue()` only makes sense when due dates are stored consistently

## Things A Junior Dev Should Notice

### This Model Has Behavior, But Only Local Behavior

The file does not decide whether checkout is allowed. That rule belongs to `LibraryManager`.

Instead, it only knows how to update itself once that decision has already been made.

### `borrowerId` Is A Link, Not An Object Reference

That is a deliberate design choice. It keeps the model simple and makes file persistence easier.

### There Are No Setters For Individual Fields

That helps reduce accidental invalid states, because callers cannot casually toggle one field at a time.

### The Invariants Are Real Even If They Are Not Declared

The code depends on a convention that checkout-related fields stay coherent together.

## Code Review Lens

If this file appears in a PR, focus on:

- whether the model still has a clear, limited job
- whether any new fields fit the domain or belong elsewhere
- whether state transitions still keep checkout fields consistent
- whether changes here require matching persistence changes

Strong review questions:

- does this method mutate more or less state than callers expect
- if a new field is added, what should happen on checkout and return
- which other files assume this field exists or is cleared

## Suggested Exercises

1. Add a `genre` field and decide what should change in storage and display.
2. Add a method that returns a human-readable summary string, then decide whether that is a good idea or presentation leakage.
3. Write down all valid and invalid combinations of the checkout-related fields.

## Observed Facts vs Inference

Observed from code:

- this file stores both identity data and checkout state
- it offers a limited set of state transitions through `checkOut` and `returnBook`
- it uses `LocalDate` for dates and `Long` for optional borrower linkage

Inference / likely intent:

- the author wanted the model to stay simple but not be completely anemic
- the lack of setters is meant to encourage safer state changes through higher-level methods
