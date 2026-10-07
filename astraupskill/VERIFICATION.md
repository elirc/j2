# Verification record and practical limits

The completed functional verification passed **53 checks**: 44 Java model, aggregate, service, and repository tests plus nine real CLI/process checks. Compilation used JDK 17 with UTF-8 and `--release 17`; Python 3 orchestrated disposable fixtures. The nine Java source/test files were then formatted consistently, and a separate compilation of the formatted files passed. Formatting used a SHA-256-verified Google Java Format 1.28.0 artifact; it did not add a runtime dependency to the project.

> **Re-check 2026-10-06 (documentation pass).** The Java suite was recompiled from the committed sources with JDK 17.0.10 into a temporary folder and printed `RESULT {"passed":true,"tests":44}`; `LibraryWorkflowTest.java` has 44 `test(...)` calls, and `scripts/verify.py` defines eight `cli(...)` scenarios plus the two-JVM race (nine process checks). `verify.py` itself was not rerun in that pass. The paragraphs below describe the original delivery run.

## What the Java suite established

Model checks cover required bounded text, control characters, normalized optional contact fields, immutable versioned edits, checkout-state consistency, date bounds, overdue comparison, return behavior, archived records, and version exhaustion. Aggregate checks cover unique identities, allocation watermarks, unique normalized nonempty emails, valid references, and one consistent active loan per book copy.

Workflow tests verify that creates and edits survive restart, unused records can be deleted without ID reuse, checkout creates one receipt, return retains accepted title/name text, archived participants cannot borrow, active loans prevent archival, and retained history prevents deletion. Search and returned collections are also checked so read operations cannot mutate accepted state accidentally.

Repository tests cover Unicode and delimiter round trips, malformed UTF-8, bounded input, truncation, exact trailer counts, strict legacy fields, invalid dates and booleans, missing legacy partners, and missing borrower references. The conversion test compares both original files before and after an accepted mutation and confirms that neither was modified. A new snapshot becomes the active source.

An injected atomic-checkout failure verifies that the manager still shows an available book, no new loan receipt, and unchanged file bytes. Separate-session tests verify stale-baseline rejection and deliberate reload recovery. Failed reload retains previously accepted in-memory values and does not authorize overwriting the malformed current file.

## What the process checks established

The nine scenarios cover create/checkout, restart with current loans, return/history, edit/archive/search, history-protected deletion, incomplete-command EOF, cancelled and confirmed unused deletion, corrupt startup, and two competing JVMs. The concurrency barrier ensures both processes load before either saves. Exactly one accepts its new book and the other reports a conflict.

Reproduce the run with `python scripts/verify.py`, setting `JAVA_HOME` when the JDK is not on PATH. Evidence goes to `.verification` unless `LIBRARY_VERIFY_RESULTS_DIR` selects another location. All application data used by the helper is temporary; no personal library directory was opened for these checks.

## Boundaries to explain in a review

This project remains a local Java CLI. It has no HTTP routes, browser UI, authentication, borrower ownership policy, external notifications, fines, renewals, or deployed database. Those are explicit course exercises or future product decisions. Rejected terminal input is not retained as a separate draft or automatically merged.

Snapshots are bounded to 10 MiB and 10,000 records per collection. File locking is cooperative, and atomic replacement depends on filesystem support. The checks do not certify power-loss recovery, directory metadata persistence, network filesystems, or multi-machine coordination. ISBN is descriptive text, and email validation is a basic local format/uniqueness rule rather than external verification.

Legacy current checkouts receive receipts using names available at import. Earlier returns already discarded by the old format cannot be reconstructed. State that limitation when presenting the new retained history. A passing suite supports the behaviors above; it is not a claim that every future web or operational requirement has already been implemented.
