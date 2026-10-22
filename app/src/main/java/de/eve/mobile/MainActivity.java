package de.eve.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;

public final class MainActivity extends Activity {
  static final int BG = Ui.BG, PANEL = Ui.RAISED, MINT = Ui.CYAN, TEXT = Ui.TEXT, MUTED = Ui.MUTED;
  EveApp app;
  LinearLayout root, timeline, attachmentRow, activityBody;
  ScrollView scroll;
  HorizontalScrollView attachmentScroll;
  TextView subtitle, heading, error, attached, activityLabel, thought, recordingCaption;
  EditText composer;
  ImageButton send, mic, history, fresh, settings, image, activityIcon;
  SignalFrame composerFrame, activityFrame;
  ArrayList<String> images;
  final ArrayList<LinearLayout> rendered = new ArrayList<>();
  final ArrayList<EveApp.Message> renderedMessages = new ArrayList<>();
  final ArrayDeque<File> audioQueue = new ArrayDeque<>();
  MediaPlayer player;
  AudioRecord recorder;
  volatile boolean recording;
  ByteArrayOutputStream recordingBytes;
  Thread recordingThread;
  String recordingError = "";
  boolean foreground, thoughtExpanded;
  int attachmentHash;
  long lastPaint;
  final Runnable redraw = () -> render(false);

  int dp(float n) {
    return Ui.dp(this, n);
  }

  GradientDrawable shape(int color, int radius) {
    return Ui.surface(this, color, radius, 0);
  }

  TextView text(String s, int size, int color) {
    return Ui.text(this, s, size, color);
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (EveApp) getApplication();
    images = app.draftImages;
    root = Ui.column(this);
    root.setPadding(dp(14), dp(4), dp(14), dp(8));
    root.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xff071323, Ui.BG, Ui.DEEP}));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            root.setPadding(
                dp(14) + bars.left, dp(4) + bars.top, dp(14) + bars.right, dp(8) + bars.bottom);
          } else
            root.setPadding(
                dp(14) + insets.getSystemWindowInsetLeft(),
                dp(4) + insets.getSystemWindowInsetTop(),
                dp(14) + insets.getSystemWindowInsetRight(),
                dp(8) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    setContentView(root);
    LinearLayout brand = Ui.row(this);
    history =
        Ui.icon(
            this,
            R.drawable.ic_sidebar,
            "Gespräche, Zeitpläne und Aufgabenlisten",
            this::conversations);
    brand.addView(history, new LinearLayout.LayoutParams(dp(48), dp(48)));
    brand.addView(Ui.logo(this, 30));
    LinearLayout identity = Ui.column(this);
    identity.setPadding(dp(9), 0, 0, 0);
    TextView name = text("EVE", 19, TEXT);
    name.setLetterSpacing(.15f);
    name.setTypeface(null, Typeface.BOLD);
    identity.addView(name);
    subtitle = text("LIVING SIGNAL", 10, MUTED);
    identity.addView(subtitle);
    brand.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
    fresh = Ui.icon(this, R.drawable.ic_plus, "Neues Gespräch", this::newConversation);
    brand.addView(fresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
    settings = Ui.icon(this, R.drawable.ic_more, "EVE-Menü", this::menu);
    brand.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(brand);
    heading = text(app.title, 13, MUTED);
    heading.setMaxLines(1);
    heading.setEllipsize(TextUtils.TruncateAt.END);
    heading.setPadding(dp(5), dp(4), dp(5), dp(10));
    root.addView(heading);
    error = text("", 13, Ui.ERROR);
    error.setTextIsSelectable(true);
    error.setPadding(dp(10), dp(10), dp(10), dp(10));
    error.setBackground(Ui.surface(this, 0xff24121d, 12, 0xff63303f));
    root.addView(error);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    timeline = Ui.column(this);
    timeline.setPadding(dp(2), dp(18), dp(2), dp(18));
    scroll.addView(timeline);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    activityFrame = new SignalFrame(this, 18);
    activityBody = Ui.column(this);
    activityBody.setPadding(dp(12), dp(4), dp(10), dp(10));
    activityFrame.addView(activityBody);
    LinearLayout signal = Ui.row(this);
    activityIcon = Ui.icon(this, R.drawable.ic_bolt, "EVEs laufende Aktivität", () -> {});
    activityIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
    signal.addView(activityIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
    activityLabel = text("EVE denkt …", 12, Ui.CYAN);
    activityLabel.setTypeface(null, Typeface.BOLD);
    activityLabel.setMaxLines(1);
    activityLabel.setEllipsize(TextUtils.TruncateAt.END);
    signal.addView(activityLabel, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton mute =
        Ui.icon(
            this,
            app.prefs.getBoolean("voice", true) ? R.drawable.ic_volume : R.drawable.ic_mute,
            "EVEs Stimme umschalten",
            () -> {
              boolean on = !app.prefs.getBoolean("voice", true);
              app.prefs.edit().putBoolean("voice", on).apply();
              if (!on) stopAudio();
              Ui.iconState(
                  (ImageButton) signal.getChildAt(2),
                  on ? R.drawable.ic_volume : R.drawable.ic_mute,
                  on ? "Stimme ausschalten" : "Stimme einschalten",
                  Ui.MUTED);
            });
    signal.addView(mute, new LinearLayout.LayoutParams(dp(38), dp(38)));
    activityBody.addView(signal);
    thought = text("", 13, MUTED);
    thought.setMaxLines(2);
    thought.setEllipsize(TextUtils.TruncateAt.END);
    thought.setLineSpacing(dp(3), 1);
    thought.setPadding(dp(10), dp(2), dp(6), dp(4));
    thought.setContentDescription("EVEs Gedanken, tippen zum Aufklappen");
    thought.setOnClickListener(
        v -> {
          thoughtExpanded = !thoughtExpanded;
          thought.setMaxLines(thoughtExpanded ? 7 : 2);
        });
    activityBody.addView(thought);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
    ap.bottomMargin = dp(10);
    root.addView(activityFrame, ap);
    composerFrame = new SignalFrame(this, 26);
    LinearLayout input = Ui.column(this);
    input.setPadding(dp(5), dp(4), dp(5), dp(5));
    composerFrame.addView(input);
    attachmentScroll = new HorizontalScrollView(this);
    attachmentRow = Ui.row(this);
    attachmentRow.setPadding(dp(8), dp(8), dp(8), dp(4));
    attachmentScroll.addView(attachmentRow);
    input.addView(attachmentScroll);
    attached = text("", 12, MINT);
    attached.setVisibility(View.GONE);
    composer = new EditText(this);
    composer.setId(1001);
    composer.setHint("Frag EVE …");
    composer.setHintTextColor(Ui.FAINT);
    composer.setTextColor(TEXT);
    composer.setTextSize(16);
    composer.setGravity(Gravity.TOP);
    composer.setMinLines(1);
    composer.setMaxLines(5);
    composer.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    composer.setPadding(dp(14), dp(12), dp(14), dp(10));
    composer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    input.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout actions = Ui.row(this);
    image = Ui.icon(this, R.drawable.ic_plus, "Anhänge hinzufügen", this::imageMenu);
    actions.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));
    recordingCaption = text("", 12, Ui.VIOLET);
    recordingCaption.setMaxLines(1);
    recordingCaption.setEllipsize(TextUtils.TruncateAt.END);
    actions.addView(recordingCaption, new LinearLayout.LayoutParams(0, -2, 1));
    mic = Ui.icon(this, R.drawable.ic_mic, "Sprachnachricht aufnehmen", this::microphone);
    actions.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));
    send = Ui.icon(this, R.drawable.ic_send, "Nachricht senden", this::send);
    send.setBackground(Ui.touch(this, Ui.CYAN, 24, 0));
    Ui.iconState(send, R.drawable.ic_send, "Nachricht senden", Ui.DEEP);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(44), dp(44));
    sp.setMargins(dp(4), 0, dp(3), 0);
    actions.addView(send, sp);
    input.addView(actions);
    root.addView(composerFrame, new LinearLayout.LayoutParams(-1, -2));
    composer.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            updateControls();
          }

          public void afterTextChanged(Editable e) {}
        });
    if (state != null) composer.setText(state.getString("draft", ""));
    updateAttachments();
    render(true);
  }

  void newConversation() {
    if (!app.configured()) {
      settings();
      return;
    }
    if (app.busy) return;
    stopAudio();
    app.create();
  }

  void menu() {
    final String[] entries = {"Zeitpläne", "Aufgabenlisten", "Verbindung und Darstellung"};
    final int[] icons = {R.drawable.ic_clock, R.drawable.ic_list, R.drawable.ic_gear};
    LinearLayout options = Ui.column(this);
    options.setPadding(dp(12), dp(8), dp(12), dp(8));
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle("EVE").setView(options).create();
    for (int i = 0; i < entries.length; i++) {
      final int index = i;
      TextView item =
          Ui.chip(
              this,
              entries[i],
              false,
              () -> {
                dialog.dismiss();
                if (index < 2) openWorkspace(index + 1);
                else settings();
              });
      item.setGravity(Gravity.CENTER_VERTICAL);
      item.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[i], 0, 0, 0);
      item.setCompoundDrawablePadding(dp(14));
      options.addView(item, new LinearLayout.LayoutParams(-1, dp(56)));
    }
    dialog.show();
  }

  void openWorkspace(int tab) {
    if (!app.configured()) {
      settings();
      return;
    }
    try {
      new WorkspaceSheet(this, tab).show();
    } catch (Exception e) {
      Toast.makeText(this, EveApp.humanError(e), Toast.LENGTH_LONG).show();
    }
  }

  void updateControls() {
    if (send == null) return;
    boolean canSend = composer.getText().toString().trim().length() > 0 || !images.isEmpty();
    send.setEnabled(!recording && (app.busy ? app.cancellable : canSend));
    send.setAlpha(send.isEnabled() ? 1 : .35f);
    Ui.iconState(
        send,
        app.busy ? R.drawable.ic_stop : R.drawable.ic_send,
        app.busy ? "Antwort abbrechen" : "Nachricht senden",
        Ui.DEEP);
    mic.setEnabled(!app.busy);
    Ui.iconState(
        mic,
        recording ? R.drawable.ic_stop : R.drawable.ic_mic,
        recording ? "Aufnahme beenden und senden" : "Sprachnachricht aufnehmen",
        recording ? Ui.VIOLET : TEXT);
    image.setEnabled(!app.busy && !recording);
    image.setAlpha(image.isEnabled() ? 1 : .35f);
    history.setEnabled(!recording);
    fresh.setEnabled(!app.busy && !recording);
    settings.setEnabled(!recording);
    composer.setEnabled(!app.busy && !recording);
    recordingCaption.setText(recording ? "Aufnahme läuft …" : app.busy ? "EVE arbeitet …" : "");
  }

  @Override
  protected void onStart() {
    super.onStart();
    foreground = true;
    app.observer = () -> render(false);
    app.audio = this::queueAudio;
    app.foreground();
    if (app.configured() && !app.connectedOnce && !app.busy) app.connect();
    app.startLiveState();
    render(true);
  }

  @Override
  protected void onStop() {
    foreground = false;
    app.stopLiveState();
    composerFrame.signal(Ui.CYAN, false, false);
    activityFrame.signal(Ui.CYAN, false, false);
    app.observer = null;
    app.audio = null;
    app.pauseObservation();
    app.main.removeCallbacks(redraw);
    if (recording) finishRecording(false);
    stopAudio();
    super.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("draft", composer.getText().toString());
  }

  void render(boolean force) {
    updateAttachments();
    updateControls();
    if (!force && System.currentTimeMillis() - lastPaint < 120) {
      app.main.removeCallbacks(redraw);
      app.main.postDelayed(redraw, 120);
      return;
    }
    lastPaint = System.currentTimeMillis();
    subtitle.setText(
        app.configured()
            ? (app.error.isEmpty()
                ? (app.online ? "LOCAL // CONNECTED" : "LOCAL // VERBINDE")
                : "LOCAL // PRÜFEN")
            : "LIVING SIGNAL");
    heading.setText(app.title);
    error.setText(app.error);
    error.setVisibility(app.error.isEmpty() ? View.GONE : View.VISIBLE);
    boolean motion = app.prefs.getBoolean("motion", true);
    String phase =
        recording
            ? "listening"
            : player != null ? "speaking" : app.turnActive ? app.liveState : "idle";
    int color =
        phase.startsWith("tool:")
            ? Ui.VIOLET
            : phase.startsWith("memory:")
                ? 0xff6aa6ff
                : phase.equals("listening")
                    ? Ui.VIOLET
                    : phase.equals("speaking") ? Ui.MINT : Ui.CYAN;
    boolean active = recording || app.turnActive || player != null;
    composerFrame.signal(color, active, motion && foreground);
    activityFrame.signal(color, active, motion && foreground);
    activityFrame.setVisibility(active ? View.VISIBLE : View.GONE);
    String phaseLabel =
        phase.equals("reconnecting")
            ? "Verbinde mit laufendem Auftrag …"
            : recording
                ? "Ich höre dir zu"
                : phase.startsWith("tool:")
                    ? "Werkzeug · " + app.liveDetail
                    : phase.startsWith("memory:")
                        ? "EVE erinnert sich"
                        : phase.equals("speaking")
                            ? "EVE spricht"
                            : phase.equals("answering") ? "EVE antwortet" : "EVE denkt nach";
    activityLabel.setText(phaseLabel);
    activityLabel.setTextColor(color);
    Ui.iconState(
        activityIcon,
        phase.startsWith("tool:")
            ? R.drawable.ic_gear
            : phase.equals("speaking")
                ? R.drawable.ic_volume
                : recording ? R.drawable.ic_mic : R.drawable.ic_bolt,
        "EVEs laufende Aktivität",
        color);
    thought.setText(app.thoughtPreview());
    thought.setVisibility(app.turnActive && !app.thought.isEmpty() ? View.VISIBLE : View.GONE);
    boolean bottom = timeline.getHeight() - scroll.getScrollY() - scroll.getHeight() < dp(100);
    if (renderedMessages.size() > app.messages.size()
        || (!renderedMessages.isEmpty()
            && !app.messages.isEmpty()
            && renderedMessages.get(0) != app.messages.get(0))) {
      timeline.removeAllViews();
      rendered.clear();
      renderedMessages.clear();
    }
    if (app.messages.isEmpty()) {
      timeline.removeAllViews();
      rendered.clear();
      renderedMessages.clear();
      LinearLayout welcome = Ui.column(this);
      welcome.setGravity(Gravity.CENTER_HORIZONTAL);
      welcome.setPadding(dp(16), dp(40), dp(16), dp(28));
      ImageView logo = Ui.logo(this, 140);
      welcome.addView(logo);
      TextView signature = Ui.eyebrow(this, "EVE // LIVING SIGNAL");
      signature.setGravity(Gravity.CENTER);
      signature.setPadding(0, dp(18), 0, dp(18));
      signature.setTextColor(MINT);
      welcome.addView(signature);
      TextView greeting = text("Was liegt an?", 25, TEXT);
      greeting.setGravity(Gravity.CENTER);
      greeting.setTypeface(null, Typeface.BOLD);
      welcome.addView(greeting);
      TextView copy =
          text(
              app.configured()
                  ? "Ein Gedanke. Eine Frage. Ein Anfang."
                  : "Verbinde dich mit deiner EVE.",
              14,
              MUTED);
      copy.setGravity(Gravity.CENTER);
      copy.setPadding(0, dp(12), 0, 0);
      welcome.addView(copy);
      timeline.addView(welcome);
      return;
    }
    if (rendered.isEmpty()) timeline.removeAllViews();
    for (int i = 0; i < app.messages.size(); i++) {
      EveApp.Message message = app.messages.get(i);
      LinearLayout body;
      if (i < rendered.size()) body = rendered.get(i);
      else {
        LinearLayout wrapper = Ui.column(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        boolean user = message.role.equals("user");
        p.setMargins(user ? dp(38) : 0, dp(5), 0, dp(20));
        timeline.addView(wrapper, p);
        if (user) {
          wrapper.setPadding(dp(16), dp(12), dp(16), dp(6));
          wrapper.setBackground(Ui.surface(this, Ui.RAISED, 20, Ui.BORDER));
        } else {
          LinearLayout label = Ui.row(this);
          label.addView(Ui.logo(this, 24));
          TextView name = Ui.eyebrow(this, "EVE");
          name.setTextColor(MINT);
          name.setPadding(dp(8), 0, 0, 0);
          label.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
          ImageButton copy =
              Ui.icon(
                  this,
                  R.drawable.ic_copy,
                  "Antwort kopieren",
                  () -> {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("EVE", message.content));
                    Toast.makeText(this, "Antwort kopiert", Toast.LENGTH_SHORT).show();
                  });
          copy.setPadding(dp(10), dp(10), dp(10), dp(10));
          label.addView(copy, new LinearLayout.LayoutParams(dp(40), dp(40)));
          wrapper.addView(label);
        }
        body = Ui.column(this);
        wrapper.addView(body);
        rendered.add(body);
        renderedMessages.add(message);
      }
      String value =
          message.content.isEmpty() && message.images.isEmpty()
              ? (app.turnActive ? "…" : "")
              : message.content;
      String renderKey = value + "\u0000" + message.images.hashCode();
      if (!renderKey.equals(body.getTag())) {
        body.setTag(renderKey);
        body.removeAllViews();
        if (message.role.equals("assistant")) {
          MarkdownView markdown = new MarkdownView(this);
          markdown.markdown(value);
          body.addView(markdown);
        } else {
          TextView plain = text(value, 16, TEXT);
          plain.setTextIsSelectable(true);
          plain.setLineSpacing(dp(3), 1);
          plain.setPadding(0, 0, 0, dp(6));
          body.addView(plain);
        }
        for (String source : message.images) body.addView(new ChatImageView(this, source));
      }
    }
    if (bottom || force) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
  }

  void updateAttachments() {
    if (attachmentRow == null) return;
    attachmentScroll.setVisibility(images.isEmpty() ? View.GONE : View.VISIBLE);
    int hash = images.hashCode();
    if (hash == attachmentHash) return;
    attachmentHash = hash;
    attachmentRow.removeAllViews();
    for (String data : new ArrayList<>(images)) {
      LinearLayout chip = Ui.row(this);
      chip.setBackground(Ui.surface(this, Ui.RAISED, 12, Ui.BORDER));
      chip.setPadding(dp(4), dp(4), dp(2), dp(4));
      ImageView preview = new ImageView(this);
      preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
      preview.setContentDescription("Angehängtes Bild");
      try {
        byte[] bytes =
            android.util.Base64.decode(
                data.substring(data.indexOf(',') + 1), android.util.Base64.DEFAULT);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;
        preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options));
      } catch (Exception ignored) {
      }
      chip.addView(preview, new LinearLayout.LayoutParams(dp(48), dp(48)));
      ImageButton remove =
          Ui.icon(
              this,
              R.drawable.ic_close,
              "Bild entfernen",
              () -> {
                images.remove(data);
                updateAttachments();
                updateControls();
              });
      chip.addView(remove, new LinearLayout.LayoutParams(dp(36), dp(48)));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
      p.rightMargin = dp(8);
      attachmentRow.addView(chip, p);
    }
  }

  void send() {
    if (app.busy) {
      app.cancel();
      stopAudio();
      return;
    }
    if (!app.configured()) {
      settings();
      return;
    }
    String value = composer.getText().toString().trim();
    if (value.length() > 32000) {
      composer.setError("Bitte höchstens 32.000 Zeichen senden.");
      return;
    }
    if (value.isEmpty() && images.isEmpty()) return;
    stopAudio();
    app.send(value, new ArrayList<>(images), null);
    if (app.busy) {
      composer.setText("");
      images.clear();
      updateAttachments();
      ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(composer.getWindowToken(), 0);
    }
  }

  void settings() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(22), dp(8), dp(22), 0);
    TextView note =
        text(
            "Die Adresse deiner laufenden EVE, wie im Browser. Unterwegs brauchst du eine"
                + " erreichbare Verbindung, etwa per VPN.",
            14,
            MUTED);
    box.addView(note);
    EditText url = new EditText(this);
    url.setSingleLine(true);
    url.setHint("https://eve.local:8790");
    url.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    url.setText(app.prefs.getString("url", ""));
    box.addView(url);
    EditText token = new EditText(this);
    token.setSingleLine(true);
    token.setHint("EVE-Web-Zugriffstoken (falls eingerichtet)");
    token.setInputType(129);
    token.setText(app.prefs.getString("token", ""));
    box.addView(token);
    CheckBox motion = new CheckBox(this);
    motion.setText("Lebendige Konturen und Animationen");
    motion.setChecked(app.prefs.getBoolean("motion", true));
    box.addView(motion);
    CheckBox voice = new CheckBox(this);
    voice.setText("EVEs Stimme auf dem Handy abspielen");
    voice.setChecked(app.prefs.getBoolean("voice", true));
    box.addView(voice);
    TextView hint =
        text(
            "Bei HTTP werden auch Token unverschlüsselt übertragen. Für öffentliche Verbindungen"
                + " HTTPS oder ein VPN verwenden. Ein Assist-Token ist hier nicht der richtige"
                + " Token.",
            12,
            MUTED);
    box.addView(hint);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Mit EVE verbinden")
            .setView(box)
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Verbinden", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        new EveApi(url.getText().toString(), token.getText().toString());
                        boolean changed =
                            !app.prefs.getString("url", "").equals(url.getText().toString().trim());
                        app.prefs
                            .edit()
                            .putString("url", url.getText().toString().trim())
                            .putString("token", token.getText().toString().trim())
                            .putBoolean("voice", voice.isChecked())
                            .putBoolean("motion", motion.isChecked())
                            .apply();
                        if (changed) {
                          app.rememberConversation("");
                          app.messages.clear();
                          app.online = false;
                          app.conversations = new org.json.JSONArray();
                          app.title = "Dein Raum für EVE";
                        }
                        stopAudio();
                        dialog.dismiss();
                        app.connect();
                      } catch (Exception e) {
                        url.setError(e.getMessage());
                      }
                    }));
    dialog.show();
  }

  void conversations() {
    openWorkspace(0);
  }

  void imageMenu() {
    if (images.size() >= 4) {
      Toast.makeText(this, "Bis zu vier Bilder pro Nachricht", Toast.LENGTH_SHORT).show();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Bild hinzufügen")
        .setItems(
            new String[] {"Kamera", "Galerie oder Dateien"},
            (d, i) -> {
              try {
                if (i == 0) {
                  Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                  capture.putExtra(MediaStore.EXTRA_OUTPUT, CameraProvider.URI);
                  capture.addFlags(
                      Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                          | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  capture.setClipData(ClipData.newRawUri("EVE Foto", CameraProvider.URI));
                  startActivityForResult(capture, 20);
                } else {
                  Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                  pick.setType("image/*");
                  pick.addCategory(Intent.CATEGORY_OPENABLE);
                  startActivityForResult(pick, 21);
                }
              } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Keine passende App gefunden", Toast.LENGTH_LONG).show();
              }
            })
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK) return;
    Uri uri =
        request == 20 ? CameraProvider.URI : request == 21 && data != null ? data.getData() : null;
    if (uri == null) return;
    app.worker.execute(
        () -> {
          try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth < 1 || bounds.outHeight < 1)
              throw new IOException("Dieses Bildformat wird nicht unterstützt.");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048)
              options.inSampleSize *= 2;
            Bitmap bitmap;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) throw new IOException("Bild konnte nicht geladen werden.");
            int orientation = 1;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              orientation =
                  new android.media.ExifInterface(in)
                      .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            } catch (IOException ignored) {
            }
            Matrix matrix = new Matrix();
            switch (orientation) {
              case 2:
                matrix.setScale(-1, 1);
                break;
              case 3:
                matrix.setRotate(180);
                break;
              case 4:
                matrix.setScale(1, -1);
                break;
              case 5:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
              case 6:
                matrix.setRotate(90);
                break;
              case 7:
                matrix.setRotate(270);
                matrix.postScale(-1, 1);
                break;
              case 8:
                matrix.setRotate(270);
                break;
            }
            Bitmap rotated =
                Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            rotated.compress(Bitmap.CompressFormat.JPEG, 85, out);
            if (rotated != bitmap) rotated.recycle();
            bitmap.recycle();
            if (out.size() > 5 * 1024 * 1024) throw new IOException("Bild ist zu groß.");
            String image =
                "data:image/jpeg;base64,"
                    + android.util.Base64.encodeToString(
                        out.toByteArray(), android.util.Base64.NO_WRAP);
            app.main.post(
                () -> {
                  if (images.size() < 4) images.add(image);
                  app.changed();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> Toast.makeText(this, EveApp.humanError(e), Toast.LENGTH_LONG).show());
          }
        });
  }

  void microphone() {
    if (recording) {
      finishRecording(true);
      return;
    }
    if (!app.configured()) {
      settings();
      return;
    }
    if (!images.isEmpty() || composer.getText().length() > 0) {
      Toast.makeText(this, "Bitte zuerst den Entwurf senden oder leeren.", Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 30);
      return;
    }
    try {
      stopAudio();
      int min =
          AudioRecord.getMinBufferSize(
              16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
      if (min < 0) throw new IOException("Mikrofon unterstützt 16 kHz nicht.");
      recorder =
          new AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              16000,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              Math.max(min, 8192));
      if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
        throw new IOException("Mikrofon ist nicht verfügbar.");
      recordingBytes = new ByteArrayOutputStream();
      recordingError = "";
      recorder.startRecording();
      recording = true;
      final AudioRecord device = recorder;
      recordingThread =
          new Thread(
              () -> {
                byte[] data = new byte[4096];
                while (recording && recordingBytes.size() < 16000 * 2 * 120) {
                  int n = device.read(data, 0, data.length);
                  if (n > 0) recordingBytes.write(data, 0, n);
                  else {
                    if (recording) recordingError = "Mikrofonaufnahme wurde unterbrochen.";
                    break;
                  }
                }
                if (recording) runOnUiThread(() -> finishRecording(false));
              },
              "eve-microphone");
      recordingThread.start();
      render(true);
    } catch (Exception e) {
      if (recorder != null) {
        recorder.release();
        recorder = null;
      }
      Toast.makeText(this, EveApp.humanError(e), Toast.LENGTH_LONG).show();
    }
  }

  void finishRecording(boolean submit) {
    recording = false;
    try {
      recorder.stop();
      recordingThread.join(1000);
    } catch (Exception ignored) {
    }
    if (recordingThread != null && recordingThread.isAlive()) {
      submit = false;
      recordingError = "Aufnahme konnte nicht abgeschlossen werden.";
    }
    if (recorder != null) {
      recorder.release();
      recorder = null;
    }
    byte[] pcm = recordingBytes == null ? new byte[0] : recordingBytes.toByteArray();
    render(true);
    if (submit && recordingError.isEmpty() && pcm.length > 1600)
      app.send("", Collections.emptyList(), wave(pcm));
    else if (foreground)
      Toast.makeText(
              this,
              recordingError.isEmpty()
                  ? "Aufnahme verworfen (maximal zwei Minuten)."
                  : recordingError,
              Toast.LENGTH_LONG)
          .show();
  }

  static byte[] wave(byte[] pcm) {
    ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
    b.put(new byte[] {'R', 'I', 'F', 'F'})
        .putInt(36 + pcm.length)
        .put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '})
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put(new byte[] {'d', 'a', 't', 'a'})
        .putInt(pcm.length)
        .put(pcm);
    return b.array();
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] grants) {
    super.onRequestPermissionsResult(r, p, grants);
    if (r == 30) {
      if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) microphone();
      else
        Toast.makeText(
                this, "Mikrofon nicht erlaubt. Du kannst weiterhin schreiben.", Toast.LENGTH_LONG)
            .show();
    }
  }

  void queueAudio(String encoded) {
    if (encoded.isEmpty() || !foreground) return;
    try {
      File f = File.createTempFile("eve-voice-", ".wav", getCacheDir());
      try (FileOutputStream out = new FileOutputStream(f)) {
        out.write(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT));
      }
      audioQueue.add(f);
      playNext();
    } catch (Exception e) {
      Toast.makeText(this, "Audio konnte nicht abgespielt werden.", Toast.LENGTH_SHORT).show();
    }
  }

  void playNext() {
    if (player != null || audioQueue.isEmpty() || !foreground) return;
    File file = audioQueue.remove();
    try {
      player = new MediaPlayer();
      player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build());
      player.setDataSource(file.getAbsolutePath());
      player.setOnPreparedListener(
          p -> {
            p.start();
            render(true);
          });
      player.setOnCompletionListener(
          p -> {
            p.release();
            player = null;
            file.delete();
            playNext();
            render(false);
          });
      player.setOnErrorListener(
          (p, w, e) -> {
            p.release();
            player = null;
            file.delete();
            playNext();
            render(false);
            return true;
          });
      player.prepareAsync();
    } catch (Exception e) {
      if (player != null) player.release();
      player = null;
      file.delete();
      playNext();
    }
  }

  void stopAudio() {
    if (player != null) {
      player.release();
      player = null;
    }
    for (File f : audioQueue) f.delete();
    audioQueue.clear();
    File[] files = getCacheDir().listFiles((d, n) -> n.startsWith("eve-voice-"));
    if (files != null) for (File f : files) f.delete();
  }
}
