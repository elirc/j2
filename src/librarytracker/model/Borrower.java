package librarytracker.model;

public final class Borrower {
  private final long id;
  private final int version;
  private final String name, email, phone;
  private final boolean active;

  public Borrower(long id, String name, String email, String phone) {
    this(id, 1, name, email, phone, true);
  }

  public Borrower(long id, int version, String name, String email, String phone, boolean active) {
    Values.identity(id, version);
    this.id = id;
    this.version = version;
    this.name = Values.text(name, 1, 160, "Name");
    this.email = Values.email(email);
    this.phone = Values.optional(phone, 40, "Phone");
    this.active = active;
  }

  public Borrower edit(String name, String email, String phone) {
    return new Borrower(id, Values.nextVersion(version), name, email, phone, active);
  }

  public Borrower setActive(boolean value) {
    return value == active
        ? this
        : new Borrower(id, Values.nextVersion(version), name, email, phone, value);
  }

  public long getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isActive() {
    return active;
  }
}
