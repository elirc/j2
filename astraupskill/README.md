# Start here: turn a library workflow into a consistency lesson

This project looks like a small book tracker, but it contains a useful mid-level engineering problem: one business action changes several related records. Checking out a book changes its availability, identifies a borrower, and creates a loan receipt. Returning it changes the current state while retaining history. The implementation makes those relationships explicit without requiring a web framework or database server.

Follow [the root setup guide](../README.md) and run against a new fictional directory. Add a book and borrower, check out the book, rename both current records, then return the book. Inspect loan history. The receipt should still show the title and borrower name that were accepted at checkout. This single experiment introduces the difference between current references and historical snapshots.

## What makes this project different from J1 and j3

J1 has one record type and j3 has accounts with a transfer pair. This project has three records that one action touches together: checking out a book changes the book's availability, creates a loan, and changes the borrower's count, and returning reverses all three. Every lesson here (snapshots, stale-edit protection, deletion versus archival) is about keeping those three consistent when one write fails. Read chapter 02's first section with that action in mind.

## Suggested study sessions

1. Read [the codebase map](01-CODEBASE-MAP.md) and identify the immutable models, aggregate validator, service, storage boundary, and terminal adapter. Draw the checkout path before changing code.
2. Read [the concepts guide](02-CONCEPTS.md). Explain why independently saving books and borrowers can produce an inconsistent workspace, even when both individual files contain valid text.
3. Complete [the worked checkout change](03-WORKED-CHANGE.md). Trace the candidate book and receipt into one accepted snapshot, including the failed-save case.
4. Run [the testing and debugging lab](04-TESTING-AND-DEBUGGING.md). Use disposable fixtures for malformed legacy files, stale sessions, and injected atomic-move failure.
5. Attempt [the practice tasks](05-PRACTICE.md), compare with [solution guidance](06-SOLUTIONS-AND-REVIEW.md), and deliver [the trace lab](07-TRACE-LAB.md) explanation.

## What you should learn to articulate

A useful acceptance statement is: "When checkout persistence fails, the book remains available and no loan receipt appears." It names both parts of the aggregate. Another is: "Renaming a borrower changes the current profile but not the name recorded on a previous loan." These statements are more precise than "Handle errors" or "Keep history."

The application preserves IDs starting at zero because existing legacy files may already use that convention. A durable next-ID watermark prevents reuse after deletion. Optional ISBN text describes a copy; it is not the copy's identity. Nonempty borrower emails are normalized and unique, while blank optional contact fields remain valid. Each of these is a deliberate data-model choice you should be able to explain.

## Keep the current scope honest

This is a CLI foundation for CRUD web development. It does not already contain authentication, HTTP authorization, browser draft recovery, or SQL tables. The exercises describe how to add those boundaries without losing the current guarantees. Use [the verification record](VERIFICATION.md) to distinguish executed evidence from proposed extensions.

At the end, present one successful checkout, one rejected stale checkout, and one failed storage write. Show where the behavior is enforced and how the user recovers. That combination demonstrates progress from writing menu handlers toward owning a feature's consistency, persistence, and operational limits.
