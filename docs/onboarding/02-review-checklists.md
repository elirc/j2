# Review Checklists

These checklists are tailored to this repo, but they also map well to similar small service-oriented applications.

## Business Logic / Services

Primary file in this repo:

- `src/librarytracker/service/LibraryManager.java`

What to look for:

- are domain rules still centralized in the service layer
- does a change preserve stable ID generation
- do checkout and return operations keep related fields in sync
- are list-returning methods still safe and unsurprising for callers

Common failure modes:

- adding new mutations in `App` instead of the manager
- updating `checkedOut` without updating `borrowerId`, `checkedOutAt`, or `dueDate`
- introducing a new rule in one path but not the corresponding inverse path
- returning internal mutable lists directly

Good questions to ask in review:

- should this rule live in `LibraryManager` instead of the caller
- what state must change together here
- if this operation succeeds, what invariants should hold afterward
- if this operation fails, is the state unchanged

Subtle bugs a junior dev might miss:

- a feature that allows checkout with invalid data because it bypasses `findBookById` or `findBorrowerById`
- a change that updates one list ordering path but not another
- hidden behavior changes caused by using current date/time in more places

## Entry Points / Handlers / UI Flow

Primary file in this repo:

- `src/librarytracker/App.java`

What to look for:

- does the menu still map cleanly to actions
- are user inputs validated before they reach business logic
- are success and failure messages still aligned with actual outcomes
- is formatting logic kept separate from mutation logic

Common failure modes:

- doing business-rule checks in the UI and drifting away from service behavior
- inconsistent prompts or menu numbering after adding features
- input loops that accidentally trap the user or skip prompts
- swallowing important I/O failures with vague messages

Good questions to ask in review:

- is this new behavior presentation-only, or is it sneaking in business rules
- can the user recover from invalid input here
- if I trace this menu option, do I see a single clear path into the service layer

Subtle bugs a junior dev might miss:

- forgetting that `Scanner.nextLine()` interactions are stateful and order-sensitive
- adding formatting logic that assumes data consistency that storage may not guarantee
- displaying a result that sounds successful even when persistence failed

## Persistence And Data Flow

Primary file in this repo:

- `src/librarytracker/storage/FileStorage.java`

What to look for:

- does file format reading still match file format writing
- are optional values handled consistently on both save and load
- does startup behavior remain safe when files do not exist
- are parse failures considered anywhere they matter

Common failure modes:

- changing saved field names in one direction only
- adding a new persisted field but forgetting load logic or save logic
- allowing special values that the simple parser cannot handle well
- writing data in a way that can silently lose information

Good questions to ask in review:

- if I save and immediately load, do I get the same logical object back
- what happens if a file is missing, blank, malformed, or partially edited
- what assumptions about ordering or separators does this format depend on

Subtle bugs a junior dev might miss:

- a trailing or missing record separator changing how the last record is parsed
- date parsing failures that break startup entirely
- "simple" clean-up logic that accidentally changes durable data format

## Domain Models And Invariants

Primary files in this repo:

- `src/librarytracker/model/Book.java`
- `src/librarytracker/model/Borrower.java`

What to look for:

- are models still small and focused
- does model behavior preserve valid state transitions
- are optional fields treated consistently

Common failure modes:

- adding too much orchestration logic into the model
- exposing setters that let callers create invalid combinations of fields
- making model fields mutable without thinking through persistence and invariants

Good questions to ask in review:

- is this behavior truly local to the model, or should it live in the service layer
- does this method keep the object internally consistent
- what assumptions do other files make about these fields

Subtle bugs a junior dev might miss:

- changing `Book.returnBook` without realizing the UI and storage both depend on the cleared fields
- adding new fields to a model but not thinking about load/save compatibility

## Async Code And Side Effects

Observed from code:

- this repo currently has no async framework or concurrency model

Why include this section anyway:

- small apps often grow into larger ones, and side effects are already present through file I/O and time access

What to look for:

- where the code touches disk
- where the code depends on current date/time
- whether constructors are doing surprising work

Common failure modes:

- moving more side effects into constructors and making startup harder to reason about
- adding background behavior later without rethinking mutable shared state

Good questions to ask in review:

- does this change introduce a new side effect
- can this logic be tested deterministically
- is there a better boundary for this time or I/O dependency

Subtle bugs a junior dev might miss:

- a test or future feature becoming flaky because `LocalDate.now()` is used directly in multiple places

## Auth / Permissions / Security-Sensitive Paths

Observed from code:

- there is no authentication or permission model in the current repo

What to look for if such features are added later:

- whether rules are enforced in the service layer, not just hidden in the UI
- whether sensitive operations have one obvious enforcement point

Common failure modes:

- adding a "restricted" menu option but not protecting the underlying service call

Good questions to ask in review:

- if a caller bypasses the UI, does the restriction still hold
- where is the real enforcement point

Subtle bugs a junior dev might miss:

- treating visibility as security

## Error Handling And Validation

What to look for:

- are invalid IDs handled gracefully
- are parse errors, missing files, and save failures surfaced clearly enough
- does each layer validate the concerns it actually owns

Common failure modes:

- validating too little at the boundary
- validating in multiple places with slightly different behavior
- catching an exception and losing useful context

Good questions to ask in review:

- what is the user-facing behavior when this fails
- what is the developer-facing behavior when this fails
- does this layer know enough to validate this safely

Subtle bugs a junior dev might miss:

- assuming blank values and malformed values are the same thing
- handling missing files but not malformed files

## Tests And Missing Coverage

Observed from code:

- there are no automated tests in the repo right now

Highest-value tests to add first:

- `LibraryManager.checkOutBook` success and failure paths
- `LibraryManager.returnBook` success and failure paths
- `FileStorage.save` followed by `load` round-trip behavior
- overdue display and due-date handling

Good questions to ask in review:

- what behavior here would be easy to break accidentally
- is there a rule or parsing path that only exists in one place and deserves a test
- if a future contributor changed this, what regression would we most want caught

Subtle bugs a junior dev might miss:

- relying on manual testing for persistence changes
- not testing both in-memory behavior and persisted behavior for the same feature

## Quick PR Review Pass For This Repo

If you only have a few minutes, ask these questions:

1. Did the change preserve the boundary between `App`, `LibraryManager`, models, and `FileStorage`?
2. Did any checkout/return state fields get out of sync?
3. If persistence changed, does save still match load?
4. If user input changed, are failure messages and retry behavior still clear?
5. Did the change create any new assumptions that are not explicitly protected by code?
