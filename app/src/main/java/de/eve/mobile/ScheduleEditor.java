package de.eve.mobile;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Native schedule editing in the schedule's own time zone, preserving server semantics. */
final class ScheduleEditor extends AlertDialog {
  interface Saved {
    void save(JSONObject changes);
  }

  final Context context;
  final JSONObject task;
  final Saved saved;
  final String[] types = {"once", "delay", "daily", "weekly", "interval"};
  final String[] labels = {
    "Einmalig", "Nach einer Wartezeit", "Täglich", "Wöchentlich", "In regelmäßigen Abständen"
  };
  final long[] units = {60, 3600, 86400, 1};
  final ArrayList<CheckBox> weekdays = new ArrayList<>();
  EditText request, amount;
  Spinner type, unit;
  CheckBox active;
  LinearLayout onceRow, timeRow, daysRow, intervalRow;
  TextView dateButton, timeButton, error;
  ZonedDateTime selected;
  ZoneId zone;
  boolean timingChanged;

  ScheduleEditor(Context c, JSONObject task, Saved saved) {
    super(c);
    context = c;
    this.task = task;
    this.saved = saved;
  }

  int dp(float n) {
    return Ui.dp(context, n);
  }

  @Override
  protected void onCreate(Bundle state) {
    try {
      zone = ZoneId.of(task.optString("timezone", ZoneId.systemDefault().getId()));
    } catch (Exception e) {
      zone = ZoneId.systemDefault();
    }
    Instant at = WorkspaceData.instant(task.optString("next_run_at"));
    selected = (at.equals(Instant.EPOCH) ? Instant.now().plusSeconds(3600) : at).atZone(zone);
    setTitle("Zeitplan bearbeiten");
    LinearLayout form = Ui.column(context);
    form.setPadding(dp(20), dp(12), dp(20), dp(12));
    form.addView(Ui.eyebrow(context, "EVES AUFTRAG"));
    Ui.gap(form, 8);
    request = Ui.input(context, "Was soll EVE tun?", WorkspaceData.request(task), 5000, true);
    form.addView(request);
    Ui.gap(form, 16);
    type = new Spinner(context);
    type.setAdapter(
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
    form.addView(type);
    String current = task.optString("schedule_type");
    int index = Arrays.asList(types).indexOf(current);
    type.setSelection(Math.max(0, index));
    onceRow = Ui.column(context);
    dateButton =
        Ui.chip(
            context,
            selected.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            false,
            () ->
                new DatePickerDialog(
                        context,
                        (v, y, m, d) -> {
                          selected =
                              selected
                                  .withYear(y)
                                  .withMonth(m + 1)
                                  .withDayOfMonth(1)
                                  .withDayOfMonth(d);
                          timingChanged = true;
                          updateDate();
                        },
                        selected.getYear(),
                        selected.getMonthValue() - 1,
                        selected.getDayOfMonth())
                    .show());
    onceRow.addView(dateButton);
    form.addView(onceRow);
    timeRow = Ui.column(context);
    JSONObject schedule = task.optJSONObject("schedule");
    if (schedule == null) schedule = new JSONObject();
    if (current.equals("daily") || current.equals("weekly"))
      selected =
          selected
              .withHour(schedule.optInt("hour", selected.getHour()))
              .withMinute(schedule.optInt("minute", selected.getMinute()));
    timeButton =
        Ui.chip(
            context,
            "",
            false,
            () ->
                new TimePickerDialog(
                        context,
                        (v, h, m) -> {
                          selected = selected.withHour(h).withMinute(m);
                          timingChanged = true;
                          updateDate();
                        },
                        selected.getHour(),
                        selected.getMinute(),
                        true)
                    .show());
    timeRow.addView(timeButton);
    TextView timezone = Ui.text(context, "Zeitzone: " + zone.getId(), 12, Ui.MUTED);
    timezone.setPadding(0, dp(6), 0, dp(10));
    timeRow.addView(timezone);
    form.addView(timeRow);
    daysRow = Ui.row(context);
    String[] days = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    Set<Integer> chosen = new HashSet<>();
    JSONArray raw = schedule.optJSONArray("weekdays");
    if (raw != null) for (int i = 0; i < raw.length(); i++) chosen.add(raw.optInt(i));
    for (int i = 0; i < 7; i++) {
      CheckBox day = new CheckBox(context);
      day.setText(days[i]);
      day.setTextSize(11);
      day.setPadding(0, 0, 0, 0);
      day.setButtonDrawable(null);
      day.setGravity(Gravity.CENTER);
      day.setChecked(chosen.contains(i));
      day.setTextColor(day.isChecked() ? Ui.CYAN : Ui.MUTED);
      day.setBackground(Ui.touch(context, Ui.RAISED, 10, Ui.BORDER));
      day.setOnCheckedChangeListener(
          (b, on) -> {
            timingChanged = true;
            day.setTextColor(on ? Ui.CYAN : Ui.MUTED);
          });
      daysRow.addView(day, new LinearLayout.LayoutParams(0, dp(44), 1));
      weekdays.add(day);
    }
    form.addView(daysRow);
    intervalRow = Ui.row(context);
    long seconds = schedule.optLong("seconds", 1800);
    int selectedUnit =
        seconds % 86400 == 0 ? 2 : seconds % 3600 == 0 ? 1 : seconds % 60 == 0 ? 0 : 3;
    amount = Ui.input(context, "Abstand", Long.toString(seconds / units[selectedUnit]), 9, false);
    amount.setInputType(InputType.TYPE_CLASS_NUMBER);
    intervalRow.addView(amount, new LinearLayout.LayoutParams(0, -2, 1));
    unit = new Spinner(context);
    unit.setAdapter(
        new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Minuten", "Stunden", "Tage", "Sekunden"}));
    unit.setSelection(selectedUnit);
    intervalRow.addView(unit, new LinearLayout.LayoutParams(0, -2, 1));
    form.addView(intervalRow);
    amount.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            timingChanged = true;
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    active = new CheckBox(context);
    active.setText("Zeitplan aktiv");
    active.setChecked(task.optString("status").equals("active"));
    form.addView(active);
    error = Ui.text(context, "", 13, Ui.ERROR);
    form.addView(error);
    type.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          int last = Math.max(0, Arrays.asList(types).indexOf(current));

          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(
              android.widget.AdapterView<?> p, View v, int position, long id) {
            if (last != position) timingChanged = true;
            last = position;
            showFields();
          }
        });
    unit.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          int last = selectedUnit;

          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(
              android.widget.AdapterView<?> p, View v, int position, long id) {
            if (last != position) timingChanged = true;
            last = position;
          }
        });
    updateDate();
    showFields();
    ScrollView scroll = new ScrollView(context);
    scroll.addView(form);
    setView(scroll);
    setButton(BUTTON_NEGATIVE, "Zurück", (d, w) -> {});
    setButton(BUTTON_POSITIVE, "Speichern", (d, w) -> {});
    super.onCreate(state);
    getButton(BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              try {
                String text = request.getText().toString().trim();
                if (text.isEmpty())
                  throw new IllegalArgumentException("Bitte EVEs Auftrag eingeben.");
                JSONObject changes =
                    new JSONObject()
                        .put("request", text)
                        .put("status", active.isChecked() ? "active" : "paused");
                if (timingChanged) {
                  String kind = types[type.getSelectedItemPosition()];
                  JSONObject s = new JSONObject();
                  if (kind.equals("once")) {
                    if (!selected.toInstant().isAfter(Instant.now()))
                      throw new IllegalArgumentException(
                          "Bitte einen zukünftigen Zeitpunkt wählen.");
                    s.put("at", selected.withSecond(0).withNano(0).toInstant().toString());
                  } else if (kind.equals("delay") || kind.equals("interval")) {
                    long value =
                        Long.parseLong(amount.getText().toString())
                            * units[unit.getSelectedItemPosition()];
                    if (value < 60 || value > 315360000L)
                      throw new IllegalArgumentException(
                          "Bitte einen Abstand zwischen einer Minute und zehn Jahren wählen.");
                    s.put("seconds", value);
                  } else {
                    s.put("hour", selected.getHour()).put("minute", selected.getMinute());
                    if (kind.equals("weekly")) {
                      JSONArray chosenDays = new JSONArray();
                      for (int i = 0; i < 7; i++)
                        if (weekdays.get(i).isChecked()) chosenDays.put(i);
                      if (chosenDays.length() == 0)
                        throw new IllegalArgumentException(
                            "Bitte mindestens einen Wochentag wählen.");
                      s.put("weekdays", chosenDays);
                    }
                  }
                  changes.put("schedule_type", kind).put("schedule", s);
                }
                saved.save(changes);
                dismiss();
              } catch (Exception e) {
                error.setText(
                    e instanceof NumberFormatException
                        ? "Bitte einen gültigen Abstand eingeben."
                        : e.getMessage());
              }
            });
  }

  void updateDate() {
    dateButton.setText(selected.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    timeButton.setText(selected.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
  }

  void showFields() {
    String kind = types[type.getSelectedItemPosition()];
    onceRow.setVisibility(kind.equals("once") ? View.VISIBLE : View.GONE);
    timeRow.setVisibility(
        kind.equals("once") || kind.equals("daily") || kind.equals("weekly")
            ? View.VISIBLE
            : View.GONE);
    daysRow.setVisibility(kind.equals("weekly") ? View.VISIBLE : View.GONE);
    intervalRow.setVisibility(
        kind.equals("delay") || kind.equals("interval") ? View.VISIBLE : View.GONE);
  }
}
