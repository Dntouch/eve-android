package de.eve.mobile;

import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;

/** Native CommonMark blocks and spans. No HTML execution or WebView. */
final class MarkdownView extends LinearLayout {
  private String source;

  MarkdownView(Context c) {
    super(c);
    setOrientation(VERTICAL);
  }

  void markdown(String value) {
    if (value.equals(source)) return;
    source = value;
    removeAllViews();
    blocks(this, MarkdownSource.parse(value));
  }

  private int dp(float n) {
    return Ui.dp(getContext(), n);
  }

  private TextView text(CharSequence value, int size) {
    TextView v = Ui.text(getContext(), "", size, Ui.TEXT);
    v.setText(value);
    v.setTextIsSelectable(true);
    v.setLinkTextColor(Ui.CYAN);
    v.setMovementMethod(LinkMovementMethod.getInstance());
    v.setLineSpacing(dp(3), 1.03f);
    return v;
  }

  private void add(LinearLayout parent, View v, int margin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(margin);
    parent.addView(v, p);
  }

  private void blocks(LinearLayout parent, Node container) {
    for (Node n = container.getFirstChild(); n != null; n = n.getNext()) block(parent, n);
  }

  private void block(LinearLayout parent, Node n) {
    if (n instanceof Heading) {
      int level = ((Heading) n).getLevel();
      TextView t = text(inline(n), level == 1 ? 25 : level == 2 ? 22 : 19);
      t.setTypeface(null, Typeface.BOLD);
      t.setPadding(0, dp(8), 0, dp(3));
      add(parent, t, 10);
    } else if (n instanceof Paragraph) {
      add(parent, text(inline(n), 16), 10);
    } else if (n instanceof BulletList || n instanceof OrderedList) {
      int index = n instanceof OrderedList ? ((OrderedList) n).getStartNumber() : 0;
      for (Node item = n.getFirstChild(); item != null; item = item.getNext()) {
        LinearLayout row = Ui.row(getContext());
        row.setGravity(Gravity.TOP);
        TextView marker = text(index == 0 ? "•" : index++ + ".", 16);
        marker.setTextColor(Ui.CYAN);
        marker.setGravity(Gravity.TOP | Gravity.RIGHT);
        marker.setPadding(0, 0, dp(9), 0);
        row.addView(marker, new LinearLayout.LayoutParams(dp(28), -2));
        LinearLayout body = Ui.column(getContext());
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        blocks(body, item);
        add(parent, row, 0);
      }
    } else if (n instanceof BlockQuote) {
      LinearLayout row = Ui.row(getContext());
      row.setGravity(Gravity.TOP);
      View line = new View(getContext());
      line.setBackgroundColor(Ui.CYAN);
      row.addView(line, new LinearLayout.LayoutParams(dp(2), -1));
      LinearLayout body = Ui.column(getContext());
      body.setPadding(dp(14), dp(4), dp(4), 0);
      blocks(body, n);
      row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
      row.setBackground(Ui.surface(getContext(), Ui.RAISED, 10, 0));
      add(parent, row, 12);
    } else if (n instanceof FencedCodeBlock) {
      FencedCodeBlock code = (FencedCodeBlock) n;
      code(parent, code.getLiteral(), code.getInfo());
    } else if (n instanceof IndentedCodeBlock) {
      code(parent, ((IndentedCodeBlock) n).getLiteral(), "");
    } else if (n instanceof ThematicBreak) {
      View line = new View(getContext());
      line.setBackgroundColor(Ui.BORDER);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
      p.setMargins(0, dp(10), 0, dp(18));
      parent.addView(line, p);
    } else if (n instanceof TableBlock) {
      table(parent, n);
    } else if (n instanceof HtmlBlock) {
      add(parent, text(((HtmlBlock) n).getLiteral(), 15), 10);
    } else {
      blocks(parent, n);
    }
  }

  private void code(LinearLayout parent, String literal, String language) {
    LinearLayout panel = Ui.column(getContext());
    panel.setBackground(Ui.surface(getContext(), Ui.DEEP, 12, Ui.BORDER));
    LinearLayout bar = Ui.row(getContext());
    bar.setPadding(dp(12), 0, 0, 0);
    TextView label =
        Ui.eyebrow(
            getContext(),
            language.trim().isEmpty()
                ? "CODE"
                : language.trim().split("\\s+")[0].toUpperCase(java.util.Locale.ROOT));
    bar.addView(label, new LinearLayout.LayoutParams(0, dp(40), 1));
    label.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton copy =
        Ui.icon(
            getContext(),
            R.drawable.ic_copy,
            "Code kopieren",
            () -> {
              ((android.content.ClipboardManager)
                      getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("EVE Code", literal));
              Toast.makeText(getContext(), "Code kopiert", Toast.LENGTH_SHORT).show();
            });
    bar.addView(copy, new LinearLayout.LayoutParams(dp(44), dp(44)));
    panel.addView(bar);
    HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    TextView code =
        text(literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal, 14);
    code.setTypeface(Typeface.MONOSPACE);
    code.setPadding(dp(14), dp(5), dp(14), dp(16));
    scroll.addView(code);
    panel.addView(scroll);
    add(parent, panel, 14);
  }

  private void table(LinearLayout parent, Node n) {
    HorizontalScrollView scroller = new HorizontalScrollView(getContext());
    TableLayout table = new TableLayout(getContext());
    table.setBackground(Ui.surface(getContext(), Ui.DEEP, 10, Ui.BORDER));
    for (Node section = n.getFirstChild(); section != null; section = section.getNext())
      for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
        android.widget.TableRow tr = new android.widget.TableRow(getContext());
        boolean header = section instanceof TableHead;
        tr.setBackgroundColor(header ? Ui.RAISED : Ui.SURFACE);
        for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
          TextView t = text(inline(cell), 14);
          t.setMinWidth(dp(96));
          t.setMaxWidth(dp(240));
          t.setPadding(dp(12), dp(12), dp(12), dp(12));
          if (header) t.setTypeface(null, Typeface.BOLD);
          if (cell instanceof TableCell) {
            TableCell.Alignment align = ((TableCell) cell).getAlignment();
            if (align == TableCell.Alignment.RIGHT) t.setGravity(Gravity.RIGHT);
            else if (align == TableCell.Alignment.CENTER) t.setGravity(Gravity.CENTER_HORIZONTAL);
          }
          tr.addView(t);
        }
        table.addView(tr);
        View divider = new View(getContext());
        divider.setBackgroundColor(Ui.BORDER);
        table.addView(divider, new TableLayout.LayoutParams(-1, dp(1)));
      }
    scroller.addView(table);
    add(parent, scroller, 14);
  }

  private SpannableStringBuilder inline(Node n) {
    SpannableStringBuilder b = new SpannableStringBuilder();
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) append(b, c);
    return b;
  }

  private void append(SpannableStringBuilder b, Node n) {
    int start = b.length();
    if (n instanceof Text) b.append(((Text) n).getLiteral());
    else if (n instanceof Code) b.append(((Code) n).getLiteral());
    else if (n instanceof SoftLineBreak || n instanceof HardLineBreak) b.append('\n');
    else if (n instanceof HtmlInline) {
      String raw = ((HtmlInline) n).getLiteral();
      b.append(raw.matches("(?i)<br\\s*/?>") ? "\n" : raw);
    } else if (n instanceof Image) {
      b.append("Bild: ");
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) append(b, c);
    } else for (Node c = n.getFirstChild(); c != null; c = c.getNext()) append(b, c);
    int end = b.length();
    if (start == end) return;
    if (n instanceof StrongEmphasis)
      b.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (n instanceof Emphasis)
      b.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (n instanceof Strikethrough)
      b.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (n instanceof Code) {
      b.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      b.setSpan(new BackgroundColorSpan(Ui.RAISED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      b.setSpan(new ForegroundColorSpan(Ui.CYAN), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    String link =
        n instanceof Link
            ? ((Link) n).getDestination()
            : n instanceof Image ? ((Image) n).getDestination() : null;
    if (link != null && MarkdownSource.safeLink(link)) {
      b.setSpan(
          new ClickableSpan() {
            @Override
            public void onClick(View v) {
              try {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
              } catch (ActivityNotFoundException ignored) {
                Toast.makeText(getContext(), "Kein Browser verfügbar", Toast.LENGTH_SHORT).show();
              }
            }
          },
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }
}
