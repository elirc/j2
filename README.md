# Library Book Tracker: consistent CRUD and loan history

A Java 17 CLI for managing book copies, borrowers, checkouts, returns, and retained loan history. It now includes versioned book/borrower editing, confirmed deletion of unused records, archive/restore, literal search, and immediate persistence. Start with [the detailed learning course](astraupskill/README.md).

## Compile and run

```powershell
javac -encoding UTF-8 --release 17 -d out "@sources.txt"
java -cp out librarytracker.App
```

The default data directory is `data`. Use a separate fictional workspace for practice:

```powershell
java -cp out librarytracker.App "practice-library"
```

IDs start at zero, preserving the original project's convention. Each book ID represents a particular copy; ISBN is optional descriptive metadata, not a unique copy identity or a validated ISBN checksum.

## Workflows

Menu options 1-7 retain add/list/register/list/check-out/return/active-loans. Option 8 reports the saved revision because every accepted mutation is already persisted. Option 9 exits. Options 10-18 add book and borrower editing, deletion of unused records, search, reload, loan history, and archive/restore.

Checkout requires an active available book and an active existing borrower. An optional due date must be within 1970-2100 and cannot precede checkout. The book's current state and a new loan receipt commit together. Return clears the current loan fields and records the return date on the retained receipt in the same snapshot.

A loan receipt keeps the title and borrower name accepted at checkout. Later renames update the current catalogue or borrower profile without rewriting historical receipt text. A book or borrower with any retained loan history cannot be deleted. After active loans are returned, archive the record to retire it while keeping references valid. Restoration makes it available again. Deleting a genuinely unused record requires typing `DELETE`; archiving and restoring require `YES`.

Borrower names and contact fields are bounded and control-free. Nonempty emails are normalized to lowercase and must be unique; the email check is a basic local format rule, not proof of ownership or deliverability. Blank emails may repeat. Search accepts `all`, `available`, `out`, or `archived` and matches title, author, and ISBN literally without interpreting a pattern.

## Storage and migration

The active format is `data/library.snapshot`, a single `LIBRARY2` aggregate containing books, borrowers, loans, a revision, and durable allocation watermarks. Text fields use canonical Base64 and the file ends with exact collection counts. The decoder validates references, unique identities, current-loan consistency, timestamps, and capacity: 10 MiB per file and 10,000 records per collection.

If the snapshot is absent, both original `books.txt` and `borrowers.txt` must be present together, or both must be absent for a new workspace. Legacy input is read strictly. On the first accepted mutation, a new snapshot is created; neither legacy file is modified. Current legacy checkouts become retained active loan receipts using the names available at import. Returns that happened before the original files were saved cannot be reconstructed.

An existing snapshot takes precedence over the historical pair. A malformed snapshot stops startup; it does not trigger a fallback that silently discards newer data. Before saving, participating processes coordinate through `library.lock` and compare the loaded baseline. Stale sessions must Reload, review the accepted records, and re-enter their intended change. A failed save leaves the manager's accepted state unchanged.

## Verify

```powershell
python scripts/verify.py
```

Use Python 3 with JDK 17 on PATH or set `JAVA_HOME`. The helper compiles into a temporary directory and checks models, aggregate references, CRUD, loan history, legacy conversion, CLI behavior, failed replacement, and competing JVMs. It never uses your normal data directory. Evidence goes to `.verification` or `LIBRARY_VERIFY_RESULTS_DIR`. Read [the verification record](astraupskill/VERIFICATION.md) for actual results and limits.

This remains a local CLI without HTTP routes, browser UI, authentication, ownership policy, notifications, fines, renewals, or deployment. Atomic replacement and cooperative locking depend on the filesystem and participating writers; no power-loss or network-filesystem certification is claimed. The course makes the path toward a policy-aware SQL-backed CRUD web application explicit. [Historical documentation](docs/HISTORICAL-README.md) is preserved for comparison.
