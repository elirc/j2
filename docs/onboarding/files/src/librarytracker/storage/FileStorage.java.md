# `src/librarytracker/storage/FileStorage.java`

## What This File Is For

This file is the persistence adapter for the application.

In plain language:

- it knows where data is stored
- it knows how books and borrowers are written to disk
- it knows how to rebuild those objects from saved files

File role:

- storage adapter
- persistence boundary
- filesystem integration layer

## Why This File Matters

This file is where in-memory objects become durable state.

That matters because persistence bugs are sticky:

- they can survive across runs
- they can corrupt data silently
- they can break startup, not just one feature path

It is also the best file for understanding how the codebase draws a boundary around side effects.

## Key Exports / Main Units

### Constructor `FileStorage(Path dataDirectory)`

- stores the base data directory
- derives paths for `books.txt` and `borrowers.txt`

### `load`

- creates the data directory if needed
- loads both books and borrowers
- returns them wrapped in `LibraryData`

### `save`

- creates the data directory if needed
- writes both books and borrowers

### Loading helpers

- `loadBooks`
- `loadBorrowers`
- `readRecords`

These methods parse the file format into Java objects.

### Saving helpers

- `saveBooks`
- `saveBorrowers`

These methods serialize Java objects into text files.

### Parsing / formatting helpers

- `blankToNull`
- `parseLongOrNull`
- `parseDateOrNull`
- `valueOrBlank`
- `clean`

### `LibraryData`

- a small helper class that returns both loaded lists together

## How It Works

### File Layout

The class writes:

- `data/books.txt`
- `data/borrowers.txt`

Each object is written as a block of `key=value` lines followed by `---`.

### Loading Flow

`load` creates the data directory and then calls `loadBooks` and `loadBorrowers`.

Each of those methods:

1. returns an empty list if the file does not exist
2. uses `readRecords` to group lines into record maps
3. converts those maps into `Book` or `Borrower` objects

### Saving Flow

`save` creates the data directory and delegates to `saveBooks` and `saveBorrowers`.

Those methods:

1. build a list of lines
2. append each field explicitly
3. add the record separator after each object
4. write all lines to the file

The explicit field-by-field approach is easy to inspect and beginner-friendly, but it also means every new persisted field must be wired in manually.

## How It Connects To Other Files

Inbound relationships:

- `LibraryManager` uses this class for startup loading and later saves

Outbound relationships:

- constructs `Book` and `Borrower`
- depends on `java.nio.file.Files` and `Path`
- parses `LocalDate` values

Important contracts:

- the save format and load format must stay aligned
- missing files should behave like empty data, not fatal errors
- optional values are stored as blank strings and reconstructed as `null`

## Things A Junior Dev Should Notice

### The File Format Is Simple On Purpose

There is no JSON library or serialization framework here. That keeps the project beginner-friendly and reduces magic.

The tradeoff is that the app now owns every detail of the serialization format.

### Parsing Is Not Defensive Against Bad Data

If a file contains an invalid number or invalid date, parsing will throw and startup can fail.

That is acceptable in a teaching project, but it is an important risk to keep in mind.

### `clean` Only Removes Newlines

That tells you something subtle:

- the format assumes values stay on one line
- it does not do more advanced escaping

If future fields can contain special characters or multiline text, this file will need more careful handling.

### Persistence Logic Is Intentionally Kept Out Of The Manager

That separation makes both files easier to reason about and easier to review.

## Code Review Lens

If this file appears in a PR, focus on:

- whether save and load still form a correct round trip
- whether new fields were added in both directions
- whether blank and optional values are handled consistently
- whether malformed file behavior got better or worse

Strong review questions:

- if I save an object and reload it, what exact data do I get back
- what happens if the file is missing its final separator
- what assumptions does this parser make that are not obvious at first glance

## Suggested Exercises

1. Add a persisted `createdAt` field to books and wire it through save and load.
2. Change `loadBooks` to skip malformed records instead of failing the whole load, then think about the tradeoffs.
3. Write down a sample `books.txt` file by hand and predict exactly what objects would be reconstructed.

## Observed Facts vs Inference

Observed from code:

- this file is the only source file doing filesystem I/O
- it uses a simple `key=value` plus `---` record format
- missing files produce empty lists
- invalid parse values are not handled with local recovery

Inference / likely intent:

- the author optimized for readability of the persistence layer over robustness
- the file format is meant to be understandable to a beginner inspecting it by hand
