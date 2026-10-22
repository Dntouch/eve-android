package de.eve.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Structured mobile workspace: conversations, schedules, and actual EVE work lists. */
final class WorkspaceSheet extends Dialog {
  final MainActivity host;
  final EveApp app;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final WorkspaceApi api;
  LinearLayout root, tabs, body, tools;
  TextView title, hint, problem;
  EditText search;
  ProgressBar progress;
  JSONArray conversations = new JSONArray(),
      schedules = new JSONArray(),
      lists = new JSONArray(),
      items = new JSONArray();
  String listId = "", listName = "", query = "";
  int tab, loaded;
  boolean busy, completed, moreConversations;

  interface Job {
    void run() throws Exception;
  }

  WorkspaceSheet(MainActivity host, int tab) {
    super(host);
    this.host = host;
    this.app = host.app;
    this.tab = tab;
    api = new WorkspaceApi(app.api());
  }

  int dp(float v) {
    return Ui.dp(host, v);
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    root = Ui.column(host);
    root.setPadding(dp(18), dp(16), dp(18), dp(12));
    root.setBackground(Ui.surface(host, Ui.BG, 26, Ui.BORDER));
    View handle = new View(host);
    handle.setBackground(Ui.surface(host, Ui.BORDER, 3, 0));
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
    hp.gravity = Gravity.CENTER_HORIZONTAL;
    hp.bottomMargin = dp(16);
    root.addView(handle, hp);
    LinearLayout header = Ui.row(host);
    LinearLayout identity = Ui.column(host);
    identity.addView(Ui.eyebrow(host, "EVE // DEIN ARBEITSRAUM"));
    title = Ui.text(host, "", 24, Ui.TEXT);
    title.setTypeface(null, Typeface.BOLD);
    identity.addView(title);
    header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        Ui.icon(host, R.drawable.ic_close, "Arbeitsraum schließen", this::dismiss),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);
    tabs = Ui.row(host);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
    tp.setMargins(0, dp(18), 0, dp(12));
    root.addView(tabs, tp);
    search = Ui.input(host, "Gespräche durchsuchen …", "", 200, false);
    search.setTextSize(14);
    search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
    search.setCompoundDrawablePadding(dp(8));
    root.addView(search);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            query = s.toString();
            renderBody();
          }

          public void afterTextChanged(Editable e) {}
        });
    hint = Ui.text(host, "", 12, Ui.MUTED);
    hint.setPadding(dp(2), dp(10), 0, dp(8));
    root.addView(hint);
    progress = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
    progress.setIndeterminate(true);
    root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
    problem = Ui.text(host, "", 14, Ui.ERROR);
    problem.setPadding(0, dp(8), 0, dp(8));
    root.addView(problem);
    tools = Ui.row(host);
    root.addView(tools);
    ScrollView scroll = new ScrollView(host);
    scroll.setFillViewport(true);
    body = Ui.column(host);
    body.setPadding(0, dp(10), 0, dp(20));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int bottom =
              android.os.Build.VERSION.SDK_INT >= 30
                  ? insets.getInsets(WindowInsets.Type.ime() | WindowInsets.Type.systemBars())
                      .bottom
                  : insets.getSystemWindowInsetBottom();
          root.setPadding(dp(18), dp(16), dp(18), dp(12) + bottom);
          return insets;
        });
    setContentView(root);
    setOnDismissListener(d -> worker.shutdown());
    load();
  }

  @Override
  public void show() {
    super.show();
    Window w = getWindow();
    if (w != null) {
      w.setBackgroundDrawableResource(android.R.color.transparent);
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams p = w.getAttributes();
      p.dimAmount = .7f;
      w.setAttributes(p);
      w.setGravity(Gravity.BOTTOM);
      w.setLayout(-1, (int) (host.getResources().getDisplayMetrics().heightPixels * .94));
      w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
  }

  void run(Job job) {
    if (busy) return;
    busy = true;
    problem.setText("");
    renderChrome();
    worker.execute(
        () -> {
          String failure = "";
          try {
            job.run();
          } catch (Exception e) {
            failure = EveApp.humanError(e);
          }
          String error = failure;
          app.main.post(
              () -> {
                if (!isShowing()) return;
                busy = false;
                problem.setText(error);
                renderChrome();
                renderBody();
              });
        });
  }

  void load() {
    run(
        () -> {
          if (tab == 0) {
            JSONArray rows = api.conversations(0).getJSONArray("conversations");
            app.main.post(
                () -> {
                  conversations = rows;
                  loaded = rows.length();
                  moreConversations = rows.length() == 100;
                });
          } else if (tab == 1) {
            JSONArray rows = api.schedules();
            app.main.post(() -> schedules = rows);
          } else {
            JSONArray rows = api.lists();
            JSONArray entries = listId.isEmpty() ? new JSONArray() : api.items(listId);
            app.main.post(
                () -> {
                  lists = rows;
                  items = entries;
                });
          }
        });
  }

  void renderChrome() {
    title.setText(
        tab == 0
            ? "Gespräche"
            : tab == 1 ? "Zeitpläne" : listId.isEmpty() ? "Aufgabenlisten" : listName);
    progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
    problem.setVisibility(problem.getText().length() > 0 ? View.VISIBLE : View.GONE);
    tabs.removeAllViews();
    String[] labels = {"Gespräche", "Zeitpläne", "Aufgaben"};
    int[] icons = {R.drawable.ic_chat, R.drawable.ic_clock, R.drawable.ic_list};
    for (int i = 0; i < 3; i++) {
      final int index = i;
      TextView button =
          Ui.chip(
              host,
              labels[i],
              tab == i,
              () -> {
                tab = index;
                query = "";
                search.setText("");
                listId = "";
                load();
              });
      button.setTextSize(12);
      button.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[i], 0, 0, 0);
      button.setCompoundDrawablePadding(dp(5));
      button.setEnabled(!busy);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
      p.setMargins(dp(2), 0, dp(2), 0);
      tabs.addView(button, p);
    }
    search.setHint(
        tab == 0
            ? "Gespräche durchsuchen …"
            : tab == 1
                ? "Zeitpläne durchsuchen …"
                : listId.isEmpty() ? "Listen durchsuchen …" : "Aufgaben durchsuchen …");
    tools.removeAllViews();
    if (tab == 2 && !listId.isEmpty())
      tools.addView(
          Ui.icon(
              host,
              R.drawable.ic_back,
              "Zurück zu allen Aufgabenlisten",
              () -> {
                listId = "";
                search.setText("");
                load();
              }),
          new LinearLayout.LayoutParams(dp(48), dp(48)));
    hint.setText(
        tab == 0
            ? "Deine Unterhaltungen, nach letzter Aktivität sortiert."
            : tab == 1
                ? "EVEs geplante und wiederkehrende Aufträge."
                : listId.isEmpty()
                    ? "Alles im Blick. Ein Schritt nach dem anderen."
                    : "Offene Aufgaben und bereits Erledigtes.");
    TextView label =
        Ui.eyebrow(
            host,
            tab == 0
                ? "UNTERHALTUNGEN"
                : tab == 1 ? "GEPLANTE AUFTRÄGE" : listId.isEmpty() ? "DEINE LISTEN" : "AUFGABEN");
    tools.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));
    label.setGravity(Gravity.CENTER_VERTICAL);
    if (tab == 0) {
      ImageButton newChat =
          Ui.icon(
              host,
              R.drawable.ic_plus,
              "Neues Gespräch",
              () -> {
                dismiss();
                host.newConversation();
              });
      newChat.setEnabled(!busy && !app.busy);
      tools.addView(newChat, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }
    if (tab == 2) {
      ImageButton add =
          Ui.icon(
              host,
              R.drawable.ic_plus,
              listId.isEmpty() ? "Neue Aufgabenliste" : "Neue Aufgabe",
              () -> {
                if (listId.isEmpty()) editList();
                else editItem(null);
              });
      add.setEnabled(!busy);
      tools.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }
    ImageButton refresh = Ui.icon(host, R.drawable.ic_refresh, "Aktualisieren", this::load);
    refresh.setEnabled(!busy);
    tools.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
  }

  LinearLayout card() {
    LinearLayout c = Ui.column(host);
    c.setPadding(dp(14), dp(14), dp(14), dp(12));
    c.setBackground(Ui.touch(host, Ui.SURFACE, 16, Ui.BORDER));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(10);
    body.addView(c, p);
    return c;
  }

  void section(String value) {
    TextView t = Ui.eyebrow(host, value.toUpperCase(Locale.GERMAN));
    t.setPadding(dp(4), dp(16), 0, dp(10));
    body.addView(t);
  }

  void empty(String heading, String copy) {
    Ui.gap(body, 32);
    TextView t = Ui.text(host, heading, 20, Ui.TEXT);
    t.setGravity(Gravity.CENTER);
    body.addView(t);
    TextView d = Ui.text(host, copy, 14, Ui.MUTED);
    d.setGravity(Gravity.CENTER);
    d.setPadding(dp(16), dp(12), dp(16), dp(20));
    body.addView(d);
  }

  boolean matches(String value) {
    return value.toLowerCase(Locale.GERMAN).contains(query.toLowerCase(Locale.GERMAN).trim());
  }

  void renderBody() {
    if (body == null) return;
    body.removeAllViews();
    if (tab == 0) renderConversations();
    else if (tab == 1) renderSchedules();
    else if (listId.isEmpty()) renderLists();
    else renderItems();
  }

  void renderConversations() {
    String group = "";
    ArrayList<JSONObject> rows = WorkspaceData.conversations(conversations, query);
    for (JSONObject item : rows) {
      String current =
          WorkspaceData.group(
              item.optString("updated_at"), LocalDate.now(), ZoneId.systemDefault());
      if (!current.equals(group)) {
        section(current);
        group = current;
      }
      boolean selected = item.optString("id").equals(app.conversationId);
      LinearLayout c = card();
      if (selected) c.setBackground(Ui.touch(host, Ui.RAISED, 16, 0xff358e8c));
      LinearLayout row = Ui.row(host);
      ImageView icon = new ImageView(host);
      icon.setImageResource(selected ? R.drawable.ic_chat : R.drawable.ic_chat);
      icon.setColorFilter(selected ? Ui.CYAN : Ui.FAINT);
      row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
      LinearLayout copy = Ui.column(host);
      copy.setPadding(dp(12), 0, 0, 0);
      TextView name = Ui.text(host, item.optString("title", "Unterhaltung"), 16, Ui.TEXT);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      copy.addView(name);
      int count = item.optInt("message_count");
      TextView meta =
          Ui.text(
              host,
              (selected ? "Aktuell · " : "")
                  + WorkspaceData.date(item.optString("updated_at"))
                  + " · "
                  + count
                  + " Nachrichten",
              11,
              selected ? Ui.CYAN : Ui.MUTED);
      meta.setPadding(0, dp(6), 0, 0);
      copy.addView(meta);
      row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      c.addView(row);
      c.setEnabled(!busy && !app.busy);
      c.setOnClickListener(
          v -> {
            if (app.busy || busy) return;
            dismiss();
            host.stopAudio();
            app.select(item.optString("id"));
          });
    }
    if (rows.isEmpty() && !busy)
      empty(
          query.isEmpty() ? "Noch kein Gespräch" : "Keine Treffer",
          query.isEmpty()
              ? "Beginne mit einer Nachricht an EVE."
              : "Versuche einen anderen Suchbegriff.");
    if (moreConversations) {
      TextView more =
          Ui.chip(
              host,
              "Ältere Gespräche laden",
              false,
              () ->
                  run(
                      () -> {
                        JSONArray next = api.conversations(loaded).getJSONArray("conversations");
                        app.main.post(
                            () -> {
                              for (int i = 0; i < next.length(); i++)
                                conversations.put(next.optJSONObject(i));
                              loaded += next.length();
                              moreConversations = next.length() == 100;
                            });
                      }));
      more.setEnabled(!busy);
      body.addView(more);
    }
  }

  void renderSchedules() {
    ArrayList<JSONObject> rows = WorkspaceData.objects(schedules);
    rows.sort(
        (a, b) -> {
          int group =
              Boolean.compare(
                  !a.optString("status").equals("active"), !b.optString("status").equals("active"));
          return group != 0
              ? group
              : WorkspaceData.instant(a.optString("next_run_at"))
                  .compareTo(WorkspaceData.instant(b.optString("next_run_at")));
        });
    int visible = 0;
    String last = "";
    for (JSONObject task : rows) {
      if (!matches(task.optString("name") + " " + WorkspaceData.request(task))) continue;
      visible++;
      String status = task.optString("status");
      String group =
          status.equals("active") ? "Aktiv" : status.equals("paused") ? "Pausiert" : "Weitere";
      if (!last.equals(group)) {
        section(group);
        last = group;
      }
      LinearLayout c = card();
      LinearLayout top = Ui.row(host);
      TextView name = Ui.text(host, task.optString("name"), 16, Ui.TEXT);
      name.setTypeface(null, Typeface.BOLD);
      top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      TextView badge =
          Ui.text(
              host,
              status.equals("active")
                  ? "AKTIV"
                  : status.equals("paused") ? "PAUSE" : status.toUpperCase(Locale.GERMAN),
              10,
              status.equals("active") ? Ui.CYAN : Ui.MUTED);
      badge.setPadding(dp(8), dp(5), dp(8), dp(5));
      badge.setBackground(Ui.surface(host, Ui.RAISED, 8, Ui.BORDER));
      top.addView(badge);
      c.addView(top);
      TextView request = Ui.text(host, WorkspaceData.request(task), 14, Ui.MUTED);
      request.setMaxLines(3);
      request.setEllipsize(TextUtils.TruncateAt.END);
      request.setPadding(0, dp(8), 0, dp(10));
      c.addView(request);
      TextView time =
          Ui.text(
              host,
              WorkspaceData.schedule(task)
                  + " · "
                  + WorkspaceData.date(task.optString("next_run_at")),
              13,
              Ui.CYAN);
      c.addView(time);
      LinearLayout actions = Ui.row(host);
      if (status.equals("active") || status.equals("paused")) {
        ImageButton toggle =
            Ui.icon(
                host,
                status.equals("paused") ? R.drawable.ic_play : R.drawable.ic_pause,
                status.equals("paused") ? "Zeitplan fortsetzen" : "Zeitplan pausieren",
                () ->
                    mutate(
                        () ->
                            api.updateSchedule(
                                task.optString("id"),
                                new JSONObject()
                                    .put(
                                        "status", status.equals("paused") ? "active" : "paused"))));
        toggle.setEnabled(!busy);
        actions.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(48)));
      }
      ImageButton edit =
          Ui.icon(
              host,
              R.drawable.ic_edit,
              "Zeitplan bearbeiten",
              () ->
                  new ScheduleEditor(
                          host,
                          task,
                          changes ->
                              mutate(() -> api.updateSchedule(task.optString("id"), changes)))
                      .show());
      edit.setEnabled(!busy);
      actions.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
      View spacer = new View(host);
      actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
      ImageButton delete =
          Ui.icon(
              host,
              R.drawable.ic_delete,
              "Zeitplan löschen",
              () ->
                  confirm(
                      "Zeitplan löschen?",
                      task.optString("name"),
                      () -> mutate(() -> api.deleteSchedule(task.optString("id")))));
      delete.setEnabled(!busy);
      actions.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
      c.addView(actions);
    }
    if (visible == 0 && !busy)
      empty(
          query.isEmpty() ? "Alles im Moment" : "Keine Treffer",
          query.isEmpty()
              ? "Noch keine Zeitpläne. Einen neuen Auftrag kannst du EVE im Chat geben."
              : "Versuche einen anderen Suchbegriff.");
  }

  void renderLists() {
    int visible = 0;
    for (JSONObject list : WorkspaceData.objects(lists)) {
      if (!matches(list.optString("name") + " " + list.optString("description"))) continue;
      visible++;
      LinearLayout c = card();
      LinearLayout row = Ui.row(host);
      TextView name = Ui.text(host, list.optString("name"), 18, Ui.TEXT);
      name.setTypeface(null, Typeface.BOLD);
      row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.icon(
              host,
              R.drawable.ic_delete,
              "Aufgabenliste löschen",
              () ->
                  confirm(
                      "Liste löschen?",
                      "„"
                          + list.optString("name")
                          + "“ und alle enthaltenen Aufgaben werden gelöscht.",
                      () -> mutate(() -> api.deleteList(list.optString("id"))))),
          new LinearLayout.LayoutParams(dp(48), dp(48)));
      c.addView(row);
      TextView count = Ui.text(host, list.optInt("open_count") + " offene Aufgaben", 13, Ui.CYAN);
      c.addView(count);
      String description = list.optString("description", "");
      if (!description.isEmpty()) {
        TextView d = Ui.text(host, description, 14, Ui.MUTED);
        d.setMaxLines(2);
        d.setEllipsize(TextUtils.TruncateAt.END);
        d.setPadding(0, dp(8), 0, 0);
        c.addView(d);
      }
      c.setOnClickListener(
          v -> {
            if (busy) return;
            listId = list.optString("id");
            listName = list.optString("name");
            search.setText("");
            load();
          });
    }
    if (visible == 0 && !busy)
      empty(
          query.isEmpty() ? "Platz für deine Vorhaben" : "Keine Treffer",
          query.isEmpty()
              ? "Lege deine erste Aufgabenliste mit ＋ an."
              : "Versuche einen anderen Suchbegriff.");
  }

  void renderItems() {
    int open = 0, done = 0, visible = 0;
    for (JSONObject item : WorkspaceData.objects(items)) {
      if (item.optString("status").equals("completed")) done++;
      else open++;
    }
    section(open + " offen · " + done + " erledigt");
    for (JSONObject item : WorkspaceData.objects(items)) {
      boolean finished = item.optString("status").equals("completed");
      if ((finished && !completed)
          || !matches(item.optString("title") + " " + item.optString("description"))) continue;
      visible++;
      LinearLayout c = card();
      LinearLayout row = Ui.row(host);
      CheckBox check = new CheckBox(host);
      check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
      check.setChecked(finished);
      check.setContentDescription(finished ? "Aufgabe wieder öffnen" : "Aufgabe erledigen");
      check.setEnabled(!busy);
      check.setOnCheckedChangeListener(
          (b, value) -> mutate(() -> api.completeItem(item.optString("id"), value)));
      row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(48)));
      TextView title = Ui.text(host, item.optString("title"), 16, finished ? Ui.FAINT : Ui.TEXT);
      if (finished)
        title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
      row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      ImageButton edit =
          Ui.icon(host, R.drawable.ic_edit, "Aufgabe bearbeiten", () -> editItem(item));
      edit.setEnabled(!busy);
      row.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
      c.addView(row);
      String description = item.optString("description", "");
      if (!description.isEmpty()) {
        MarkdownView markdown = new MarkdownView(host);
        markdown.markdown(description);
        c.addView(markdown);
      }
      JSONArray links = item.optJSONArray("context_links");
      if (links != null && links.length() > 0) {
        JSONObject link = links.optJSONObject(0);
        if (link != null && !link.optString("conversation_id").isEmpty()) {
          TextView context =
              Ui.chip(
                  host,
                  "Zugehöriges Gespräch",
                  false,
                  () -> {
                    if (app.busy) return;
                    dismiss();
                    app.select(link.optString("conversation_id"));
                  });
          c.addView(context);
        }
      }
    }
    if (visible == 0 && !busy)
      empty(
          query.isEmpty() ? "Hier ist alles erledigt" : "Keine Treffer",
          query.isEmpty()
              ? "Füge mit ＋ die nächste Aufgabe hinzu."
              : "Versuche einen anderen Suchbegriff.");
    if (done > 0) {
      TextView toggle =
          Ui.chip(
              host,
              (completed ? "Erledigte ausblenden" : "Erledigte anzeigen") + " (" + done + ")",
              completed,
              () -> {
                completed = !completed;
                renderBody();
              });
      body.addView(toggle);
    }
    if (items.length() >= 1000)
      body.addView(
          Ui.text(host, "Die ersten 1.000 Aufgaben dieser Liste werden angezeigt.", 12, Ui.MUTED));
  }

  void mutate(Job job) {
    run(
        () -> {
          job.run();
          if (tab == 1) {
            JSONArray rows = api.schedules();
            app.main.post(() -> schedules = rows);
          } else {
            JSONArray rows = api.lists();
            JSONArray entries = listId.isEmpty() ? new JSONArray() : api.items(listId);
            app.main.post(
                () -> {
                  lists = rows;
                  items = entries;
                });
          }
        });
  }

  void confirm(String heading, String message, Runnable yes) {
    if (busy) return;
    new AlertDialog.Builder(host)
        .setTitle(heading)
        .setMessage(message)
        .setNegativeButton("Behalten", null)
        .setPositiveButton("Löschen", (d, w) -> yes.run())
        .show();
  }

  void editList() {
    if (busy) return;
    LinearLayout form = Ui.column(host);
    form.setPadding(dp(20), dp(12), dp(20), 0);
    EditText name = Ui.input(host, "Name der Liste", "", 120, false);
    form.addView(name);
    Ui.gap(form, 12);
    EditText description = Ui.input(host, "Beschreibung (optional)", "", 2000, true);
    form.addView(description);
    AlertDialog dialog =
        new AlertDialog.Builder(host)
            .setTitle("Neue Aufgabenliste")
            .setView(form)
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Anlegen", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    w -> {
                      if (name.getText().toString().trim().isEmpty()) {
                        name.setError("Bitte einen Namen eingeben.");
                        return;
                      }
                      dialog.dismiss();
                      mutate(
                          () ->
                              api.createList(
                                  name.getText().toString().trim(),
                                  description.getText().toString()));
                    }));
    dialog.show();
  }

  void editItem(JSONObject item) {
    if (busy) return;
    LinearLayout form = Ui.column(host);
    form.setPadding(dp(20), dp(12), dp(20), 0);
    EditText title =
        Ui.input(host, "Titel", item == null ? "" : item.optString("title"), 240, false);
    form.addView(title);
    Ui.gap(form, 12);
    EditText description =
        Ui.input(
            host,
            "Beschreibung (optional)",
            item == null ? "" : item.optString("description"),
            5000,
            true);
    form.addView(description);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(host)
            .setTitle(item == null ? "Neue Aufgabe" : "Aufgabe bearbeiten")
            .setView(form)
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Speichern", null);
    if (item != null)
      builder.setNeutralButton(
          "Löschen",
          (d, w) ->
              confirm(
                  "Aufgabe löschen?",
                  item.optString("title"),
                  () -> mutate(() -> api.deleteItem(item.optString("id")))));
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    w -> {
                      if (title.getText().toString().trim().isEmpty()) {
                        title.setError("Bitte einen Titel eingeben.");
                        return;
                      }
                      dialog.dismiss();
                      mutate(
                          () ->
                              api.saveItem(
                                  item == null ? null : item.optString("id"),
                                  listId,
                                  title.getText().toString().trim(),
                                  description.getText().toString()));
                    }));
    dialog.show();
  }
}
