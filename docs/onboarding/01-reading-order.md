# Reading Order

## Recommended Reading Path

This repo is small enough that you can understand the full system, but the order still matters. Read it in layers so you learn the vocabulary first, then the rules, then the runtime flow.

## Stage 1: Learn The Domain Vocabulary

Read:

- `src/librarytracker/model/Book.java`
- `src/librarytracker/model/Borrower.java`

Why this comes first:

- these files define the nouns used everywhere else
- the rest of the app only makes sense once you know what state a book and borrower can hold

What to learn:

- which fields are core versus optional
- how checkout state is represented on a `Book`
- how the system links a book to a borrower through `borrowerId`

After this stage, you should understand:

- what data the system remembers
- which fields together define "available" versus "checked out"
- that `Borrower` is intentionally simple compared with `Book`

## Stage 2: Learn The Rules

Read:

- `src/librarytracker/service/LibraryManager.java`

Why this comes second:

- once you know the domain objects, this file shows how the app protects its invariants
- this is the best file for understanding the app's judgment, not just its data

What to learn:

- where IDs are assigned
- where checkout/return rules live
- how the app finds related records
- which operations mutate in-memory state

After this stage, you should understand:

- why the app uses a service layer at all
- why the UI should not directly edit books or borrowers
- which checks prevent obviously invalid operations

## Stage 3: Learn The Runtime Flow

Read:

- `src/librarytracker/App.java`

Why this comes third:

- now you can recognize the manager calls and understand the menu loop as orchestration instead of noise

What to learn:

- how the process starts
- how menu options map to service calls
- how input validation works at the CLI boundary
- how status text is derived for display

After this stage, you should understand:

- how a user action turns into a service operation
- which logic is presentation-only versus rule-enforcing
- where success and error messages come from

## Stage 4: Learn Persistence And Failure Modes

Read:

- `src/librarytracker/storage/FileStorage.java`

Why this comes last:

- persistence makes the most sense after you already know what the app stores and why
- this file shows the durability tradeoffs and parsing assumptions

What to learn:

- how saved files map to model objects
- which values are optional on disk
- how startup behaves when files are missing
- what kinds of file corruption would be dangerous

After this stage, you should understand:

- how the app survives between runs
- how in-memory state becomes file data
- why persistence is both simple and a little fragile

## Stage 5: Use The Docs To Build Judgment

Read:

- `docs/onboarding/00-codebase-map.md`
- `docs/onboarding/02-review-checklists.md`
- the per-file explainers under `docs/onboarding/files/`

Why this stage matters:

- code tells you what exists
- onboarding docs should help you reason about why it exists and what to be careful about when changing it

What to learn:

- how to spot architectural boundaries
- how to review a change for hidden breakage
- how to separate facts from interpretation

After this stage, you should understand:

- where a change belongs
- what nearby files are likely to be affected
- how to review beyond "does it compile"

## Suggested Tracing Exercises

### Exercise 1: Trace One Checkout From User Input To Saved Data

Start in `App.checkOutBook`, follow the call into `LibraryManager.checkOutBook`, then inspect how `Book.checkOut` changes state and how `FileStorage.saveBooks` eventually writes that state to disk.

What you should notice:

- validation happens before mutation
- mutation happens before persistence
- display logic is separate from mutation logic

### Exercise 2: Trace One Return Path, Including The Failure Cases

Start in `App.returnBook`, follow the "book not found" and "book not checked out" branches in `LibraryManager.returnBook`, then inspect `Book.returnBook`.

What you should notice:

- the failure paths are part of the product behavior, not just edge cases
- the return operation is really a state reset of several related fields

### Exercise 3: Trace Startup From Entry Point To In-Memory State

Start in `App.main`, follow construction into `LibraryManager`, then into `FileStorage.load`, `loadBooks`, and `loadBorrowers`.

What you should notice:

- constructors do real work here
- object creation has side effects at startup because loading happens during manager creation

### Exercise 4: Trace How Overdue Status Is Computed

Find where a due date is entered, where it is stored, where `LocalDate.now()` is used, and where overdue status is shown to the user.

What you should notice:

- the app stores due dates as data, but computes overdue status at read/display time
- time logic is split across layers, which is manageable now but important to remember during future changes

## What A Junior Developer Should Practice While Reading

### Ask "Where does this rule live?"

If you see a rule, identify whether it belongs in the CLI, the model, the service, or storage.

### Ask "What state changes together?"

For checkout and return, several fields move together. Practice spotting those clusters.

### Ask "What happens if the input is wrong?"

This codebase is small, so error handling is easy to trace and worth studying.

### Ask "What assumptions are only implied?"

Some invariants are enforced by convention rather than a validator. Those are common bug sources in real systems too.
