package de.eve.mobile;

import java.time.*;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.json.*;

public final class ContentTest {
  static void check(boolean b, String label) {
    if (!b) throw new AssertionError(label);
  }

  static int count(Node root, Class<?> type) {
    int n = type.isInstance(root) ? 1 : 0;
    for (Node child = root.getFirstChild(); child != null; child = child.getNext())
      n += count(child, type);
    return n;
  }

  public static void main(String[] args) throws Exception {
    Node root =
        MarkdownSource.parse(
            "## Überschrift\n\n"
                + "**fett** und *kursiv* mit `Code` und ~~alt~~.\n\n"
                + "- Eins\n"
                + "  - Unterpunkt\n\n"
                + "> Zitat\n\n"
                + "| Bereich | Status |\n"
                + "| --- | --- |\n"
                + "| EVE | bereit |\n\n"
                + "```java\n"
                + "if (a < b) send(\"<script>\");\n"
                + "```\n\n"
                + "[Website](https://example.com)\n");
    check(count(root, Heading.class) == 1, "Heading lost");
    check(count(root, StrongEmphasis.class) == 1, "Bold lost");
    check(count(root, Emphasis.class) == 1, "Italic lost");
    check(count(root, Code.class) == 1, "Inline code lost");
    check(count(root, Strikethrough.class) == 1, "Strikethrough lost");
    check(count(root, BulletList.class) == 2, "Nested list lost");
    check(count(root, BlockQuote.class) == 1, "Quote lost");
    check(count(root, TableBlock.class) == 1, "Table not parsed");
    check(count(root, TableCell.class) == 4, "Table cells lost");
    check(count(root, FencedCodeBlock.class) == 1, "Code block lost");
    Node incomplete = MarkdownSource.parse("## Stream\n\n**Noch nicht fertig");
    check(incomplete != null, "Incomplete stream must render");
    for (String safe :
        new String[] {"https://example.com", "http://eve.local", "mailto:eve@example.com"})
      check(MarkdownSource.safeLink(safe), "Safe link rejected");
    for (String unsafe :
        new String[] {
          "javascript:alert(1)",
          "file:///etc/passwd",
          "intent://secret",
          "content://private",
          "//example.com",
          "https://bad link"
        }) check(!MarkdownSource.safeLink(unsafe), "Unsafe link accepted: " + unsafe);
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    LocalDate today = LocalDate.of(2026, 10, 21);
    check(
        WorkspaceData.group("2026-10-20T23:30:00Z", today, berlin).equals("Heute"),
        "Conversation groups must use device timezone");
    check(
        WorkspaceData.group("2026-10-19T23:30:00Z", today, berlin).equals("Gestern"),
        "Yesterday group wrong");
    check(
        WorkspaceData.group("2026-10-17T12:00:00Z", today, berlin).equals("Letzte 7 Tage"),
        "Week group wrong");
    check(
        WorkspaceData.group("2026-09-17T12:00:00Z", today, berlin).equals("Älter"),
        "Archive group wrong");
    JSONArray conversations =
        new JSONArray()
            .put(new JSONObject().put("title", "Küche").put("updated_at", "2026-10-01T00:00:00Z"))
            .put(
                new JSONObject()
                    .put("title", "KÜCHE Licht")
                    .put("updated_at", "2026-10-21T00:00:00Z"));
    check(
        WorkspaceData.conversations(conversations, "küche")
            .get(0)
            .getString("title")
            .equals("KÜCHE Licht"),
        "Search/order not locale-aware");
    check(
        ThoughtText.excerpt("  Eine\n  Idee. ").equals("Eine Idee."),
        "Thought whitespace not normalized");
    String longThought = new String(new char[300]).replace('\0', 'a') + "🌙";
    String shortThought = ThoughtText.excerpt(longThought);
    check(
        shortThought.codePointCount(0, shortThought.length()) == 242,
        "Thought not reduced to 240 codepoints");
    check(shortThought.endsWith("🌙"), "Thought excerpt split Unicode");
    check(
        ThoughtText.excerpt("Originaler Gedanke").equals("Originaler Gedanke"),
        "Thought text was rewritten");
    JSONArray attachments=new JSONArray().put(new JSONObject().put("id","image-1").put("media_type","image/png").put("url","https://untrusted.example/steal")).put(new JSONObject().put("id","document").put("media_type","application/pdf"));
    check(ChatImages.sources(attachments).size()==1,"Image filtering lost");
    check(ChatImages.sources(attachments).get(0).equals("/api/conversation-assets/image-1"),"Image token could leak to arbitrary URL");
    check(!ChatImages.assetPath("https://evil.example/image")&&!ChatImages.assetPath("//evil.example/image")&&!ChatImages.assetPath("/api/config"),"Unsafe asset path accepted");
    System.out.println(
        "PASS: Markdown blocks, nested lists, tables, incomplete streams, safe links, conversation"
            + " dates/search and reduced thought excerpts");
  }
}
