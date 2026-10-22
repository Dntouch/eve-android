package de.eve.mobile;

import java.time.*;
import java.time.format.*;
import java.util.*;
import org.json.*;

final class WorkspaceData {
  static final Locale DE = Locale.GERMAN;

  static Instant instant(String raw) {
    try {
      return OffsetDateTime.parse(raw).toInstant();
    } catch (Exception e) {
      try {
        return Instant.parse(raw);
      } catch (Exception ignored) {
        return Instant.EPOCH;
      }
    }
  }

  static String group(String timestamp, LocalDate today, ZoneId zone) {
    LocalDate date = instant(timestamp).atZone(zone).toLocalDate();
    if (date.equals(today)) return "Heute";
    if (date.equals(today.minusDays(1))) return "Gestern";
    if (!date.isBefore(today.minusDays(6))) return "Letzte 7 Tage";
    return "Älter";
  }

  static String date(String raw) {
    Instant at = instant(raw);
    return at.equals(Instant.EPOCH)
        ? "Noch kein Termin"
        : DateTimeFormatter.ofPattern("dd.MM. · HH:mm", DE)
            .withZone(ZoneId.systemDefault())
            .format(at);
  }

  static ArrayList<JSONObject> objects(JSONArray array) {
    ArrayList<JSONObject> rows = new ArrayList<>();
    if (array != null)
      for (int i = 0; i < array.length(); i++) {
        JSONObject o = array.optJSONObject(i);
        if (o != null) rows.add(o);
      }
    return rows;
  }

  static ArrayList<JSONObject> conversations(JSONArray array, String search) {
    ArrayList<JSONObject> rows = objects(array);
    String query = search.toLowerCase(DE).trim();
    rows.removeIf(o -> !o.optString("title").toLowerCase(DE).contains(query));
    rows.sort(
        (a, b) -> instant(b.optString("updated_at")).compareTo(instant(a.optString("updated_at"))));
    return rows;
  }

  static String schedule(JSONObject task) {
    String type = task.optString("schedule_type");
    switch (type) {
      case "daily":
        return "Täglich";
      case "weekly":
        return "Wöchentlich";
      case "interval":
        return "Regelmäßig";
      case "delay":
        return "Verzögert";
      default:
        return "Einmalig";
    }
  }

  static String request(JSONObject task) {
    JSONObject action = task.optJSONObject("action");
    if (action == null) return task.optString("name");
    for (String key : new String[] {"request", "raw_message", "message"})
      if (!action.optString(key, "").isEmpty()) return action.optString(key);
    return task.optString("name");
  }
}
