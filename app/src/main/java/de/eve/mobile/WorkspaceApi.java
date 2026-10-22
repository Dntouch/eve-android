package de.eve.mobile;

import org.json.*;

/** Existing EVE web contracts, kept separate from the presentation. */
final class WorkspaceApi {
  interface Transport {
    JSONObject json(String method, String path, JSONObject body) throws Exception;
  }

  final Transport api;

  WorkspaceApi(EveApi api) {
    this.api = api::json;
  }

  WorkspaceApi(Transport transport) {
    this.api = transport;
  }

  JSONObject conversations(int offset) throws Exception {
    return api.json("GET", "/api/conversations?limit=100&offset=" + offset, null);
  }

  JSONArray schedules() throws Exception {
    return api.json("GET", "/api/tasks?limit=200", null).getJSONArray("tasks");
  }

  JSONArray lists() throws Exception {
    return api.json("GET", "/api/work-lists", null).getJSONArray("lists");
  }

  JSONArray items(String list) throws Exception {
    return api.json(
            "GET", "/api/work-items?status=all&limit=1000&list_id=" + EveApi.path(list), null)
        .getJSONArray("items");
  }

  void updateSchedule(String id, JSONObject changes) throws Exception {
    api.json("PATCH", "/api/tasks/" + EveApi.path(id), changes);
  }

  void deleteSchedule(String id) throws Exception {
    api.json("DELETE", "/api/tasks/" + EveApi.path(id), null);
  }

  void createList(String name, String description) throws Exception {
    api.json(
        "POST",
        "/api/work-lists",
        new JSONObject().put("name", name).put("description", description));
  }

  void deleteList(String id) throws Exception {
    api.json("DELETE", "/api/work-lists/" + EveApi.path(id), null);
  }

  void saveItem(String id, String list, String title, String description) throws Exception {
    JSONObject body =
        new JSONObject().put("list_id", list).put("title", title).put("description", description);
    api.json(
        id == null ? "POST" : "PATCH",
        id == null ? "/api/work-items" : "/api/work-items/" + EveApi.path(id),
        body);
  }

  void completeItem(String id, boolean complete) throws Exception {
    api.json(
        "PATCH",
        "/api/work-items/" + EveApi.path(id),
        new JSONObject().put("status", complete ? "completed" : "open"));
  }

  void deleteItem(String id) throws Exception {
    api.json("DELETE", "/api/work-items/" + EveApi.path(id), null);
  }
}
