package de.eve.mobile;

/** A clearly marked excerpt of EVE's actual reasoning, never an invented summary. */
final class ThoughtText {
  static String excerpt(String thought) {
    String text = thought.trim().replaceAll("\\s+", " ");
    int count = text.codePointCount(0, text.length());
    return count > 240 ? "… " + text.substring(text.offsetByCodePoints(0, count - 240)) : text;
  }
}
