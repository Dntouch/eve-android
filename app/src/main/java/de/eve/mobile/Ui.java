package de.eve.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared EVE Visual System tokens, matching the server's web/styles.css. */
final class Ui {
  static final int BG = 0xff020711,
      DEEP = 0xff010308,
      SURFACE = 0xff05111b,
      RAISED = 0xff061820,
      BORDER = 0xff16363e,
      TEXT = 0xffe8fbfa,
      MUTED = 0xff91afb2,
      FAINT = 0xff607f85,
      CYAN = 0xff63ecdf,
      VIOLET = 0xff9d63ff,
      MINT = 0xff69e6b7,
      ERROR = 0xffff637d;

  static int dp(Context c, float v) {
    return Math.round(c.getResources().getDisplayMetrics().density * v);
  }

  static GradientDrawable surface(Context c, int color, int radius, int border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    if (border != 0) d.setStroke(dp(c, 1), border);
    return d;
  }

  static android.graphics.drawable.Drawable touch(Context c, int color, int radius, int border) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x3363ecdf), surface(c, color, radius, border), null);
  }

  static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    return t;
  }

  static TextView eyebrow(Context c, String value) {
    TextView t = text(c, value, 10, FAINT);
    t.setLetterSpacing(.14f);
    t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  static ImageButton icon(Context c, int drawable, String label, Runnable action) {
    ImageButton b = new ImageButton(c);
    b.setImageResource(drawable);
    b.setImageTintList(ColorStateList.valueOf(TEXT));
    b.setContentDescription(label);
    b.setTooltipText(label);
    b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    b.setScaleType(ImageView.ScaleType.FIT_CENTER);
    b.setBackground(touch(c, Color.TRANSPARENT, 24, 0));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static void iconState(ImageButton b, int drawable, String label, int tint) {
    b.setImageResource(drawable);
    b.setContentDescription(label);
    b.setTooltipText(label);
    b.setImageTintList(ColorStateList.valueOf(tint));
  }

  static ImageView logo(Context c, int size) {
    ImageView v = new ImageView(c);
    v.setImageResource(R.drawable.eve_logo);
    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return v;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static void gap(LinearLayout l, int height) {
    View v = new View(l.getContext());
    l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), height)));
  }

  static TextView chip(Context c, String text, boolean selected, Runnable click) {
    TextView t = text(c, text, 13, selected ? CYAN : MUTED);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
    t.setBackground(touch(c, selected ? RAISED : Color.TRANSPARENT, 14, selected ? BORDER : 0));
    t.setOnClickListener(v -> click.run());
    return t;
  }

  static EditText input(Context c, String hint, String value, int max, boolean multi) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setHintTextColor(FAINT);
    e.setTextColor(TEXT);
    e.setTextSize(16);
    e.setText(value);
    e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
    e.setBackground(surface(c, DEEP, 12, BORDER));
    e.setSingleLine(!multi);
    if (multi) {
      e.setMinLines(2);
      e.setMaxLines(6);
      e.setGravity(Gravity.TOP);
    }
    e.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(max)});
    return e;
  }
}
