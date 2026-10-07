# Practice: grow the library toward a CRUD web service

Every exercise here touches the book/loan pair and depends on the borrower, so start each one by writing down which records change, which must not, and what the reader sees if the save fails after the candidate is built. Work on a branch or a scratch copy with fictional data.

Each exercise has a **Goal** and a **Check**. Checks are dependency-free `node --test` files (Node 18+ and JDK 17 on PATH, nothing to install). Keep them *outside* the repo, for example in a sibling folder `../j2-practice/`, and run them from the repo root. The shared helper compiles `src/` into the OS temp folder on every run (allow up to a minute), then drives the real CLI through stdin. Each input line follows the prompt order in the [01 menu table](01-CODEBASE-MAP.md#the-cli-menu).

## Setup: the shared helper

Save as `../j2-practice/lib-harness.mjs`:

```js
// Shared helper for the 05-PRACTICE checks. Dependency-free: needs Node 18+ and JDK 17 on PATH.
// Run from the j2 repo root, e.g.  node --test ../j2-practice/ex1-overdue.test.mjs
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const repo = process.env.LIB_REPO ?? process.cwd();
const classes = mkdtempSync(join(tmpdir(), 'j2-classes-'));
const sources = [];
(function walk(d) {
  for (const n of readdirSync(d)) {
    const p = join(d, n);
    if (statSync(p).isDirectory()) walk(p);
    else if (p.endsWith('.java')) sources.push(p);
  }
})(join(repo, 'src'));
execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes, ...sources]);

/** A fresh, empty data directory under the OS temp folder. */
export const workspace = () => mkdtempSync(join(tmpdir(), 'j2-data-'));

/** Drive the real CLI: each array item is one line of stdin. */
export function cli(dir, lines) {
  const r = spawnSync('java', ['-cp', classes, 'librarytracker.App', dir], {
    input: lines.join('\n') + '\n', encoding: 'utf8',
  });
  return { code: r.status, out: r.stdout, err: r.stderr };
}

/** Write a LIBRARY2 snapshot directly (lets a check create past-dated loans the clock forbids). */
export function writeSnapshot(dir, { revision = 1, nextBook, nextBorrower, nextLoan, books = [], borrowers = [], loans = [] }) {
  const b64 = (s) => Buffer.from(s ?? '', 'utf8').toString('base64');
  const rows = [
    'LIBRARY2',
    [revision, nextBook, nextBorrower, nextLoan].join('\t'),
    ...books.map((b) => ['B', b.id, b.version ?? 1, b64(b.title), b64(b.author), b64(b.isbn), b.active ?? true,
      !!b.borrowerId || b.borrowerId === 0, b.borrowerId ?? '', b.out ?? '', b.due ?? ''].join('\t')),
    ...borrowers.map((p) => ['P', p.id, p.version ?? 1, b64(p.name), b64(p.email), b64(p.phone), p.active ?? true].join('\t')),
    ...loans.map((l) => ['L', l.id, l.bookId, l.borrowerId, b64(l.title), b64(l.name), l.out, l.due ?? '', l.returned ?? ''].join('\t')),
    ['END', books.length, borrowers.length, loans.length].join('\t'),
    '',
  ];
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'library.snapshot'), rows.join('\n'));
}
```

`writeSnapshot` exists because checkout always uses today's clock and rejects a due date before checkout (`Book.java` constructor), so no CLI script can create an overdue loan. The helper writes a valid `LIBRARY2` file instead; `LibraryData` still validates it at startup.

## Exercise 0: prove the harness against current behaviour

**Goal:** before changing anything, pin three facts about the delivered code: the first loan id is 0, a receipt keeps the names accepted at checkout after both records are renamed, and a snapshot whose book and loan disagree is refused at startup with exit code 1.

**Check:** `node --test ../j2-practice/ex0-baseline.test.mjs` passes on the unmodified repo (verified 2026-10-06: 3/3).

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cli, workspace, writeSnapshot } from './lib-harness.mjs';

test('first loan id is 0 and the receipt keeps the accepted names after renames', () => {
  const dir = workspace();
  const r = cli(dir, [
    '1', 'Reliable systems', 'Ada', '',      // book 0
    '3', 'Sam Reader', '', '',              // borrower 0
    '5', '0', '0', '',                      // check out book 0 to borrower 0, no due date
    '10', '0', 'Reliable systems 2e', 'Ada', '',
    '12', '0', 'Sam Rivera', '', '',
    '16', '9',
  ]);
  assert.equal(r.code, 0);
  assert.match(r.out, /Loan 0\./);
  assert.match(r.out, /Loan 0 \| Book 0: Reliable systems \| Borrower 0: Sam Reader \| Out \S+ \| Due none \| Returned active/);
});

test('option 2 flags a past-due loan as OVERDUE (display only, nothing stored)', () => {
  const dir = workspace();
  writeSnapshot(dir, {
    nextBook: 1, nextBorrower: 1, nextLoan: 1,
    books: [{ id: 0, version: 2, title: 'Old', author: 'A', borrowerId: 0, out: '2020-01-01', due: '2020-01-10' }],
    borrowers: [{ id: 0, name: 'Pat' }],
    loans: [{ id: 0, bookId: 0, borrowerId: 0, title: 'Old', name: 'Pat', out: '2020-01-01', due: '2020-01-10' }],
  });
  const r = cli(dir, ['2', '9']);
  assert.equal(r.code, 0, r.err);
  assert.match(r.out, /ID 0 \/ v2 \| Old \| A \| Checked out to 0 \/ due 2020-01-10 \(OVERDUE\)/);
});

test('a snapshot whose book says checked-out but has no active loan is refused at startup', () => {
  const dir = workspace();
  writeSnapshot(dir, {
    nextBook: 1, nextBorrower: 1, nextLoan: 0,
    books: [{ id: 0, version: 2, title: 'Old', author: 'A', borrowerId: 0, out: '2020-01-01' }],
    borrowers: [{ id: 0, name: 'Pat' }],
  });
  const r = cli(dir, ['9']);
  assert.equal(r.code, 1);
  assert.match(r.err, /Current book state and active loan disagree/);
});
```

## Exercise 1: an overdue projection (menu 19)

**Goal:** add `LibraryManager.getOverdueBooks(LocalDate today)` and menu option 19 that lists only overdue books, sorted by due date, then book id. Reuse `Book.isOverdue` so "due today" is not overdue. Do not store an overdue flag. Pass the date in explicitly so a test can choose it.

**Check:** `node --test ../j2-practice/ex1-overdue.test.mjs`. On the delivered code it fails (`actual: []`, because 19 prints "Choose a listed menu number."). It passes once ids come back as `[1, 2, 0]`: books 1 and 2 share a due date and tie-break by id, book 3 has no due date, and book 4 is due in 2099.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cli, workspace, writeSnapshot } from './lib-harness.mjs';

test('option 19 lists only overdue loans, oldest due date first, ties by book id', () => {
  const dir = workspace();
  const book = (id, due) => ({ id, version: 2, title: 'T' + id, author: 'A', borrowerId: 0, out: '2020-01-01', due });
  const loan = (id, due) => ({ id, bookId: id, borrowerId: 0, title: 'T' + id, name: 'Pat', out: '2020-01-01', due });
  writeSnapshot(dir, {
    nextBook: 5, nextBorrower: 1, nextLoan: 5,
    books: [book(0, '2020-03-01'), book(1, '2020-02-01'), book(2, '2020-02-01'), book(3, undefined), book(4, '2099-01-01')],
    borrowers: [{ id: 0, name: 'Pat' }],
    loans: [loan(0, '2020-03-01'), loan(1, '2020-02-01'), loan(2, '2020-02-01'), loan(3, undefined), loan(4, '2099-01-01')],
  });
  const r = cli(dir, ['19', '9']);
  assert.equal(r.code, 0, r.err);
  // the first listed row follows the "Choose: " prompt on the same line, so no ^ anchor
  const ids = [...r.out.matchAll(/\bID (\d+) \/ v\d+ \|[^\n]*OVERDUE/g)].map((m) => Number(m[1]));
  assert.deepEqual(ids, [1, 2, 0]);
});
```

## Exercise 2: renew a loan (menu 20)

**Goal:** add menu 20 (prompts: Book ID, New due date) backed by `renewLoan(bookId, version, due)`. It must replace the book's `dueDate` and the active receipt's `dueDate` in one `commit`, keep the original checkout date, bump the book version, and reject a returned book or a date before checkout. `LibraryData` already refuses a book and an active loan whose due dates disagree (`LibraryData.java:53-58`), so updating only one of them fails at the commit instead of slipping through.

**Check:** `node --test ../j2-practice/ex2-renew.test.mjs`. It fails on the delivered code and passes once both receipts agree after a restart and both invalid renewals print `Change not accepted`.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cli, workspace } from './lib-harness.mjs';

const plus = (days) => { const d = new Date(); d.setDate(d.getDate() + days);
  return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-'); };

test('option 20 renews the book and its active receipt together', () => {
  const dir = workspace();
  const r = cli(dir, [
    '1', 'Book', 'Author', '', '3', 'Reader', '', '',
    '5', '0', '0', plus(7),
    '20', '0', plus(14),
    '7', '16', '9',
  ]);
  assert.equal(r.code, 0, r.err);
  assert.match(r.out, new RegExp(`Checked out to 0 / due ${plus(14)}`));
  assert.match(r.out, new RegExp(`Loan 0 \| .*\| Due ${plus(14)} \| Returned active`));
  // restart: the renewal survived as one accepted snapshot
  assert.match(cli(dir, ['7', '9']).out, new RegExp(`due ${plus(14)}`));
});

test('renewal before the checkout date, or of a returned book, is not accepted', () => {
  const dir = workspace();
  const r = cli(dir, [
    '1', 'Book', 'Author', '', '3', 'Reader', '', '',
    '5', '0', '0', plus(7),
    '20', '0', plus(-1),
    '6', '0',
    '20', '0', plus(30),
    '16', '9',
  ]);
  assert.equal((r.out.match(/Change not accepted/g) ?? []).length, 2, r.out);
  assert.match(r.out, new RegExp(`Due ${plus(7)} \| Returned `));
});
```

Stretch (no check given): decide whether renewal should keep the old due date as an event. If it does, the snapshot needs a new row type and a new header (`LIBRARY3`), not an extra column that `LIBRARY2` would reject as an "Invalid loan row".

## Exercise 3: port the aggregate rules for a web/SQL version

**Goal:** a web API or SQL importer needs the same invariants that `LibraryData`'s constructor enforces (`LibraryData.java:14-60`). Write `../j2-practice/library-rules.mjs` that exports `validateLibrary(data)` and throws on: an id at or above its watermark, duplicate nonempty emails, a loan referencing a missing book or borrower, two active loans for one copy, a checked-out book without its active loan (or the reverse), active-loan fields that disagree with the book, and an active loan held by an archived borrower.

**Check:** `node --test ../j2-practice/ex3-rules.test.mjs`. It fails until the module exists and passes (8/8) with a faithful port. Each rejection case corresponds to an `IllegalArgumentException` in `LibraryData`.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateLibrary } from './library-rules.mjs';

const ok = () => ({
  nextBookId: 2, nextBorrowerId: 2, nextLoanId: 1,
  books: [
    { id: 0, checkedOut: true, borrowerId: 0, checkedOutAt: '2026-01-01', dueDate: null },
    { id: 1, checkedOut: false, borrowerId: null, checkedOutAt: null, dueDate: null },
  ],
  borrowers: [{ id: 0, email: 'a@x.io', active: true }, { id: 1, email: null, active: false }],
  loans: [{ id: 0, bookId: 0, borrowerId: 0, checkedOutAt: '2026-01-01', dueDate: null, returnedAt: null }],
});

test('a consistent library passes', () => assert.doesNotThrow(() => validateLibrary(ok())));

const broken = {
  'id at or above its watermark': (d) => { d.nextBookId = 1; },
  'duplicate nonempty email': (d) => { d.borrowers[1].email = 'a@x.io'; },
  'loan pointing at a missing borrower': (d) => { d.loans[0].borrowerId = 7; },
  'checked-out book without an active loan': (d) => { d.loans[0].returnedAt = '2026-01-02'; },
  'second active loan for the same copy': (d) => { d.nextLoanId = 2; d.loans.push({ ...d.loans[0], id: 1 }); },
  'active loan whose due date disagrees with the book': (d) => { d.loans[0].dueDate = '2026-02-01'; },
  'active loan held by an archived borrower': (d) => { d.borrowers[0].active = false; },
};
for (const [name, mutate] of Object.entries(broken)) {
  test(`rejects: ${name}`, () => { const d = ok(); mutate(d); assert.throws(() => validateLibrary(d)); });
}
```

## Exercise 4: a dry-run importer for the relational migration

**Goal:** the first step of a SQL migration is reading the existing file without changing it. Write `../j2-practice/snapshot-import.mjs` exporting `importSnapshot(text)`, which returns `{ revision, nextBookId, nextBorrowerId, nextLoanId, books, borrowers, loans }` with Base64 text decoded (empty string becomes `null`, matching `Values.optional`). It must refuse a snapshot whose `END` trailer is missing or whose counts do not match, just as `FileStorage.decode` does (`FileStorage.java:285-287`). Then sketch the tables it would feed: a `loans` row keeps `book_title` and `borrower_name` as accepted text, and a partial unique index on `loans(book_id) WHERE returned_at IS NULL` replaces the "one active loan per copy" check.

**Check:** `node --test ../j2-practice/ex4-import.test.mjs`. It fails until the module exists and passes (2/2) when it decodes a snapshot that the real CLI wrote. That snapshot covers five commits, a deleted id that is not reused, and an email the CLI stored in lowercase.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cli, workspace } from './lib-harness.mjs';
import { importSnapshot } from './snapshot-import.mjs';

test('dry-run import of a snapshot the real CLI wrote', () => {
  const dir = workspace();
  cli(dir, ['1', 'Pipes | and = signs', 'Ada', '978-0', '1', 'Second', 'Bo', '', '3', 'Sam Reader', 'SAM@X.IO', '',
    '5', '0', '0', '', '11', '1', 'DELETE', '9']);
  const text = readFileSync(join(dir, 'library.snapshot'), 'utf8');
  const r = importSnapshot(text);
  assert.equal(r.revision, 5);                       // add, add, register, checkout, delete = 5 commits
  assert.deepEqual([r.nextBookId, r.nextBorrowerId, r.nextLoanId], [2, 1, 1]); // deleted id 1 is not reused
  assert.equal(r.books.length, 1);
  assert.equal(r.books[0].title, 'Pipes | and = signs');       // Base64 keeps delimiter characters safe (a real TAB is rejected as a control char)
  assert.equal(r.borrowers[0].email, 'sam@x.io');    // stored already normalised
  assert.equal(r.loans[0].borrowerName, 'Sam Reader');
  assert.equal(r.loans[0].returnedAt, null);
});

test('refuses a truncated snapshot instead of importing a partial one', () => {
  const dir = workspace();
  cli(dir, ['1', 'Only', 'Ada', '', '9']);
  const text = readFileSync(join(dir, 'library.snapshot'), 'utf8');
  assert.throws(() => importSnapshot(text.replace(/END\t1\t0\t0\n$/, '')));
  assert.throws(() => importSnapshot(text.replace('END\t1', 'END\t2')));
});
```

A real TAB typed into a title never reaches the file: `Values.text` rejects ISO control characters, so the CLI answers `Change not accepted`. Base64 protects `|`, `=`, and non-ASCII text, not control characters.

Finish each exercise with a short review: the concrete behaviour, the line where it is enforced, the check you ran, and what remains unproven. A design-only extension such as an authenticated HTTP adapter belongs in that review as a limit, not a claim. [06](06-SOLUTIONS-AND-REVIEW.md) has solution sketches and review findings.
