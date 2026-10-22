package de.eve.mobile;

import java.net.URI;
import java.util.Arrays;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

final class MarkdownSource {
  private static final Parser PARSER =
      Parser.builder()
          .extensions(Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()))
          .build();

  static Node parse(String source) {
    return PARSER.parse(source);
  }

  static boolean safeLink(String destination) {
    try {
      String scheme = URI.create(destination).getScheme();
      return "https".equalsIgnoreCase(scheme)
          || "http".equalsIgnoreCase(scheme)
          || "mailto".equalsIgnoreCase(scheme);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
