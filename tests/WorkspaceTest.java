package de.eve.mobile;

import java.util.*;
import org.json.*;

public final class WorkspaceTest {
  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  public static void main(String[] args) throws Exception {
    ArrayList<String> calls = new ArrayList<>();
    ArrayList<JSONObject> payloads = new ArrayList<>();
    WorkspaceApi api =
        new WorkspaceApi(
            (method, path, body) -> {
              calls.add(method + " " + path);
              payloads.add(body);
              return new JSONObject()
                  .put("conversations", new JSONArray())
                  .put("tasks", new JSONArray())
                  .put("lists", new JSONArray())
                  .put("items", new JSONArray());
            });
    api.conversations(100);
    check(calls.get(0).endsWith("offset=100"), "Older conversation page lost");
    api.items("home /1");
    check(
        calls.get(1).contains("status=all") && calls.get(1).endsWith("home%20%2F1"),
        "List filtering or safe encoding lost");
    api.completeItem("i1", true);
    check(calls.get(2).equals("PATCH /api/work-items/i1"), "Task completion must use PATCH");
    check(payloads.get(2).getString("status").equals("completed"), "Wrong completed status");
    api.completeItem("i1", false);
    check(payloads.get(3).getString("status").equals("open"), "Task reopening lost");
    api.updateSchedule("s1", new JSONObject().put("status", "paused"));
    check(calls.get(4).equals("PATCH /api/tasks/s1"), "Wrong schedule route");
    check(
        payloads.get(4).length() == 1 && !payloads.get(4).has("schedule"),
        "Pausing must not reschedule");
    api.saveItem(null, "l1", "Einkauf", "- Kaffee");
    check(calls.get(5).equals("POST /api/work-items"), "Creation must not overwrite a task");
    check(
        payloads.get(5).getString("description").equals("- Kaffee"),
        "Markdown description changed");
    api.saveItem("i1", "l1", "Neu", "Beschreibung");
    check(calls.get(6).equals("PATCH /api/work-items/i1"), "Edit must not create another task");
    api.createList("Zuhause", "Beschreibung");
    check(calls.get(7).equals("POST /api/work-lists"), "Wrong list route");
    api.deleteSchedule("s1");
    api.deleteItem("i1");
    api.deleteList("l1");
    check(
        calls.get(8).startsWith("DELETE ")
            && calls.get(9).startsWith("DELETE ")
            && calls.get(10).startsWith("DELETE "),
        "Deletes must use explicit DELETE");
    check(calls.size() == 11, "Unexpected extra mutation or retry");
    System.out.println(
        "PASS: workspace pagination, list scoping, completion/reopening, schedule pause without"
            + " rescheduling, create/edit/delete contracts");
  }
}
