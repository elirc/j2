# `src/librarytracker/model/Borrower.java`

## What This File Is For

This file defines the `Borrower` domain model.

In plain language:

- it represents one person who can borrow books
- it stores identity and contact information

File role:

- domain model
- supporting entity

## Why This File Matters

This file is smaller than `Book`, but it is still important because it shows a useful architectural contrast:

- not every model needs heavy behavior
- some entities are mostly data carriers

Understanding that difference helps a junior developer avoid overengineering.

## Key Exports / Main Units

### Constructor `Borrower(long id, String name, String email, String phone)`

- creates a borrower with stable identity and optional contact info

### Getter methods

- `getId`
- `getName`
- `getEmail`
- `getPhone`

These expose borrower data for the service, UI, and storage layers.

## How It Works

This file is intentionally simple.

It stores:

- one required identifying field: `name`
- one stable ID
- two optional contact fields: `email` and `phone`

There is no behavior beyond construction and read access. That is not a weakness. It reflects that the app currently does not need more borrower-specific rules in the model itself.

## How It Connects To Other Files

Inbound relationships:

- `LibraryManager` creates borrowers and looks them up by ID
- `FileStorage` reconstructs borrowers from saved data
- `App` formats borrowers for display

Outbound relationships:

- none beyond standard Java types

Important contracts:

- the `id` is how other files refer to a borrower
- optional contact fields may be `null`

## Things A Junior Dev Should Notice

### Simplicity Can Be Good Design

A common beginner mistake is to think every class needs lots of methods. This class is a good counterexample.

### The Relationship To Books Is Indirect

`Borrower` does not know which books it has checked out. That lookup is handled elsewhere through IDs.

### Optional Fields Matter In Review

Because `email` and `phone` may be `null`, display and persistence code must treat them carefully.

## Code Review Lens

If this file appears in a PR, focus on:

- whether new fields really belong on the borrower entity
- whether new mutability introduces risk without clear value
- whether changes require updates in storage and UI formatting

Strong review questions:

- do we need behavior here, or is another layer a better home
- if this field is optional, how do save/load and display handle it
- if this field becomes required, what existing data would break

## Suggested Exercises

1. Add a `membershipNumber` field and trace every file that would need to change.
2. Add borrower validation in the service layer and explain why you did not put it in this model.
3. Decide whether `Borrower` should ever know its currently checked-out books, and explain the tradeoffs.

## Observed Facts vs Inference

Observed from code:

- this file contains only fields, a constructor, and getters
- `email` and `phone` are treated as optional by surrounding code
- other files use `id` to refer to borrowers

Inference / likely intent:

- the author kept this class intentionally lightweight because current borrower behavior is simple
- the codebase treats borrower records more like reference data than active rule-owning objects
