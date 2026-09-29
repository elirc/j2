package librarytracker.model;

import java.time.LocalDate;
import java.util.Locale;

public final class Values {
  private Values() {}

  public static String text(String value, int min, int max, String name) {
    if (value == null) value = "";
    String clean = value.strip();
    if (clean.length() < min
        || clean.length() > max
        || clean.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(
          name + " must contain " + min + ".." + max + " characters without controls.");
    return clean;
  }

  public static String optional(String value, int max, String name) {
    String clean = text(value, 0, max, name);
    return clean.isEmpty() ? null : clean;
  }

  public static String email(String value) {
    String clean = optional(value, 254, "Email");
    if (clean != null && !clean.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Enter an email with a name and domain.");
    return clean == null ? null : clean.toLowerCase(Locale.ROOT);
  }

  public static void identity(long id, int version) {
    if (id < 0 || id == Long.MAX_VALUE || version < 1)
      throw new IllegalArgumentException("Invalid identity or version.");
  }

  public static int nextVersion(int current) {
    if (current == Integer.MAX_VALUE) throw new IllegalStateException("Record version exhausted.");
    return current + 1;
  }

  public static LocalDate date(LocalDate value) {
    if (value == null || value.getYear() < 1970 || value.getYear() > 2100)
      throw new IllegalArgumentException("Date must be within 1970..2100.");
    return value;
  }
}
