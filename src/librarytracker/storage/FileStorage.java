package librarytracker.storage;

import static java.nio.file.StandardOpenOption.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import librarytracker.model.*;

/** One atomic aggregate replaces the original independently written book/borrower files. */
public class FileStorage {
  public static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
  private final Path directory, snapshotFile, booksFile, borrowersFile;
  private byte[] baseline, legacyBooks, legacyBorrowers;
  private LibraryData current;

  public FileStorage(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
    snapshotFile = this.directory.resolve("library.snapshot");
    booksFile = this.directory.resolve("books.txt");
    borrowersFile = this.directory.resolve("borrowers.txt");
  }

  public Path snapshotPath() {
    return snapshotFile;
  }

  private static byte[] read(Path path) throws IOException {
    if (Files.isSymbolicLink(path))
      throw new IOException("Symbolic-link data files are unsupported.");
    if (!Files.exists(path)) return null;
    if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES)
      throw new IOException("Invalid data file or size exceeds 10 MiB.");
    byte[] bytes;
    try (var input = Files.newInputStream(path)) {
      bytes = input.readNBytes(MAX_BYTES + 1);
    }
    if (bytes.length > MAX_BYTES) throw new IOException("File exceeds 10 MiB.");
    return bytes;
  }

  public synchronized LibraryData load() throws IOException {
    byte[] snap = read(snapshotFile), books = null, borrowers = null;
    LibraryData parsed;
    if (snap != null) parsed = decode(snap);
    else {
      books = read(booksFile);
      borrowers = read(borrowersFile);
      if ((books == null) != (borrowers == null))
        throw new IOException(
            "Both legacy files are required; missing data is not an empty collection.");
      parsed = books == null ? LibraryData.empty() : legacy(books, borrowers);
    }
    baseline = snap;
    legacyBooks = books;
    legacyBorrowers = borrowers;
    current = parsed;
    return parsed;
  }

  public synchronized void save(LibraryData next) throws IOException {
    if (current == null) throw new IOException("Load before saving.");
    if (current.revision() == Long.MAX_VALUE
        || next.revision() != current.revision() + 1
        || next.nextBookId() < current.nextBookId()
        || next.nextBorrowerId() < current.nextBorrowerId()
        || next.nextLoanId() < current.nextLoanId())
      throw new IOException("Revision and allocation watermarks cannot move backward.");
    byte[] encoded = encode(next);
    Files.createDirectories(directory);
    Path lockPath = directory.resolve("library.lock");
    synchronized (LOCKS.computeIfAbsent(lockPath, p -> new Object())) {
      if (Files.isSymbolicLink(lockPath))
        throw new IOException("Symbolic-link locks are unsupported.");
      try (FileChannel channel = FileChannel.open(lockPath, CREATE, WRITE);
          var lock = channel.lock()) {
        if (!Arrays.equals(baseline, read(snapshotFile))
            || (baseline == null
                && (!Arrays.equals(legacyBooks, read(booksFile))
                    || !Arrays.equals(legacyBorrowers, read(borrowersFile)))))
          throw new IOException(
              "Library changed in another session. Reload and review before retrying.");
        Path temporary = Files.createTempFile(directory, ".library-save-", ".tmp");
        try {
          try (FileChannel out = FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING)) {
            ByteBuffer bytes = ByteBuffer.wrap(encoded);
            while (bytes.hasRemaining()) out.write(bytes);
            out.force(true);
          }
          replaceAtomically(temporary, snapshotFile);
          baseline = encoded;
          legacyBooks = null;
          legacyBorrowers = null;
          current = next;
        } finally {
          Files.deleteIfExists(temporary);
        }
      }
    }
  }

  protected void replaceAtomically(Path source, Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private static String utf8(byte[] bytes) throws IOException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  private static String text(String value) {
    return Base64.getEncoder()
        .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
  }

  private static String plain(String value) throws IOException {
    byte[] bytes = Base64.getDecoder().decode(value);
    if (!Base64.getEncoder().encodeToString(bytes).equals(value))
      throw new IOException("Non-canonical Base64.");
    return utf8(bytes);
  }

  private static String nullable(Object value) {
    return value == null ? "" : value.toString();
  }

  private static long number(String value) {
    if (!value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid integer.");
    return Long.parseLong(value);
  }

  private static boolean bool(String value) {
    if (!value.equals("true") && !value.equals("false"))
      throw new IllegalArgumentException("Invalid boolean.");
    return value.equals("true");
  }

  private static LocalDate date(String value) {
    return value.isEmpty() ? null : LocalDate.parse(value);
  }

  public static byte[] encode(LibraryData data) throws IOException {
    StringBuilder out =
        new StringBuilder("LIBRARY2\n")
            .append(data.revision())
            .append('\t')
            .append(data.nextBookId())
            .append('\t')
            .append(data.nextBorrowerId())
            .append('\t')
            .append(data.nextLoanId())
            .append('\n');
    for (Book b : data.books())
      out.append("B\t")
          .append(b.getId())
          .append('\t')
          .append(b.getVersion())
          .append('\t')
          .append(text(b.getTitle()))
          .append('\t')
          .append(text(b.getAuthor()))
          .append('\t')
          .append(text(b.getIsbn()))
          .append('\t')
          .append(b.isActive())
          .append('\t')
          .append(b.isCheckedOut())
          .append('\t')
          .append(nullable(b.getBorrowerId()))
          .append('\t')
          .append(nullable(b.getCheckedOutAt()))
          .append('\t')
          .append(nullable(b.getDueDate()))
          .append('\n');
    for (Borrower b : data.borrowers())
      out.append("P\t")
          .append(b.getId())
          .append('\t')
          .append(b.getVersion())
          .append('\t')
          .append(text(b.getName()))
          .append('\t')
          .append(text(b.getEmail()))
          .append('\t')
          .append(text(b.getPhone()))
          .append('\t')
          .append(b.isActive())
          .append('\n');
    for (Loan l : data.loans())
      out.append("L\t")
          .append(l.id())
          .append('\t')
          .append(l.bookId())
          .append('\t')
          .append(l.borrowerId())
          .append('\t')
          .append(text(l.bookTitle()))
          .append('\t')
          .append(text(l.borrowerName()))
          .append('\t')
          .append(l.checkedOutAt())
          .append('\t')
          .append(nullable(l.dueDate()))
          .append('\t')
          .append(nullable(l.returnedAt()))
          .append('\n');
    out.append("END\t")
        .append(data.books().size())
        .append('\t')
        .append(data.borrowers().size())
        .append('\t')
        .append(data.loans().size())
        .append('\n');
    byte[] encoded = out.toString().getBytes(StandardCharsets.UTF_8);
    if (encoded.length > MAX_BYTES) throw new IOException("Snapshot exceeds 10 MiB.");
    return encoded;
  }

  public static LibraryData decode(byte[] bytes) throws IOException {
    if (bytes.length > MAX_BYTES) throw new IOException("Snapshot exceeds 10 MiB.");
    try {
      String[] lines = utf8(bytes).split("\n", -1);
      if (lines.length < 4 || !lines[0].equals("LIBRARY2") || !lines[lines.length - 1].isEmpty())
        throw new IOException("Invalid or truncated library snapshot.");
      String[] header = lines[1].split("\t", -1);
      if (header.length != 4) throw new IOException("Invalid header.");
      var books = new ArrayList<Book>();
      var borrowers = new ArrayList<Borrower>();
      var loans = new ArrayList<Loan>();
      for (int i = 2; i < lines.length - 2; i++) {
        String[] f = lines[i].split("\t", -1);
        switch (f[0]) {
          case "B" -> {
            if (f.length != 11) throw new IOException("Invalid book row.");
            books.add(
                new Book(
                    number(f[1]),
                    Math.toIntExact(number(f[2])),
                    plain(f[3]),
                    plain(f[4]),
                    plain(f[5]),
                    bool(f[6]),
                    bool(f[7]),
                    f[8].isEmpty() ? null : number(f[8]),
                    date(f[9]),
                    date(f[10])));
          }
          case "P" -> {
            if (f.length != 7) throw new IOException("Invalid borrower row.");
            borrowers.add(
                new Borrower(
                    number(f[1]),
                    Math.toIntExact(number(f[2])),
                    plain(f[3]),
                    plain(f[4]),
                    plain(f[5]),
                    bool(f[6])));
          }
          case "L" -> {
            if (f.length != 9) throw new IOException("Invalid loan row.");
            loans.add(
                new Loan(
                    number(f[1]),
                    number(f[2]),
                    number(f[3]),
                    plain(f[4]),
                    plain(f[5]),
                    date(f[6]),
                    date(f[7]),
                    date(f[8])));
          }
          default -> throw new IOException("Unknown row type.");
        }
      }
      if (!lines[lines.length - 2].equals(
          "END\t" + books.size() + "\t" + borrowers.size() + "\t" + loans.size()))
        throw new IOException("Invalid trailer or record counts.");
      return new LibraryData(
          number(header[0]),
          number(header[1]),
          number(header[2]),
          number(header[3]),
          books,
          borrowers,
          loans);
    } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
      throw new IOException("Invalid library data: " + e.getMessage(), e);
    }
  }

  private static List<Map<String, String>> records(byte[] bytes, Set<String> keys)
      throws IOException {
    var records = new ArrayList<Map<String, String>>();
    var record = new LinkedHashMap<String, String>();
    String[] lines = utf8(bytes).split("\r?\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.isEmpty() && i == lines.length - 1) continue;
      if (line.equals("---")) {
        if (!record.keySet().equals(keys)) throw new IOException("Legacy record missing fields.");
        records.add(Map.copyOf(record));
        record.clear();
      } else {
        int split = line.indexOf('=');
        if (split < 1
            || !keys.contains(line.substring(0, split))
            || record.putIfAbsent(line.substring(0, split), line.substring(split + 1)) != null)
          throw new IOException("Unknown, duplicate or malformed legacy field.");
      }
    }
    if (!record.isEmpty()) throw new IOException("Legacy record missing closing separator.");
    return records;
  }

  private static LibraryData legacy(byte[] bookBytes, byte[] borrowerBytes) throws IOException {
    try {
      var books = new ArrayList<Book>();
      var borrowers = new ArrayList<Borrower>();
      var loans = new ArrayList<Loan>();
      for (var f :
          records(
              bookBytes,
              Set.of(
                  "id",
                  "title",
                  "author",
                  "isbn",
                  "checkedOut",
                  "borrowerId",
                  "checkedOutAt",
                  "dueDate")))
        books.add(
            new Book(
                number(f.get("id")),
                f.get("title"),
                f.get("author"),
                f.get("isbn"),
                bool(f.get("checkedOut")),
                f.get("borrowerId").isEmpty() ? null : number(f.get("borrowerId")),
                date(f.get("checkedOutAt")),
                date(f.get("dueDate"))));
      for (var f : records(borrowerBytes, Set.of("id", "name", "email", "phone")))
        borrowers.add(
            new Borrower(number(f.get("id")), f.get("name"), f.get("email"), f.get("phone")));
      for (Book b : books)
        if (b.isCheckedOut()) {
          Borrower borrower =
              borrowers.stream()
                  .filter(p -> p.getId() == b.getBorrowerId())
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException("Legacy loan references missing borrower."));
          loans.add(
              new Loan(
                  loans.size(),
                  b.getId(),
                  borrower.getId(),
                  b.getTitle(),
                  borrower.getName(),
                  b.getCheckedOutAt(),
                  b.getDueDate(),
                  null));
        }
      return new LibraryData(
          0,
          books.stream().mapToLong(Book::getId).max().orElse(-1) + 1,
          borrowers.stream().mapToLong(Borrower::getId).max().orElse(-1) + 1,
          loans.size(),
          books,
          borrowers,
          loans);
    } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
      throw new IOException("Invalid legacy library: " + e.getMessage(), e);
    }
  }
}
