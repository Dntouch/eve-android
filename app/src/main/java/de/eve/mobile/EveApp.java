package de.eve.mobile;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Keeps a running reply across rotation without retaining an Activity. */
public final class EveApp extends Application {
  final Handler main = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final ExecutorService controls = Executors.newSingleThreadExecutor();
  SharedPreferences prefs;
  volatile Runnable observer;
  java.util.function.Consumer<String> audio;
  JSONArray conversations = new JSONArray();
  final ArrayList<Message> messages = new ArrayList<>();
  final ArrayList<String> draftImages = new ArrayList<>();
  final ChatImageLoader imageLoader = new ChatImageLoader(this);
  String conversationId = "", title = "Dein Raum für EVE", status = "Bereit", error = "";
  volatile String turnId = "";
  boolean busy, online, cancellable, connectedOnce;
  volatile boolean turnActive;
  private boolean recoveryAttempted;
  private volatile EveApi observedApi;

  void foreground() {
    recoveryAttempted = false;
    resumePendingTurn();
  }

  void pauseObservation() {
    // Only detach once EVE has supplied a durable ID. Never cancel the server turn.
    if (turnActive && TurnRecovery.canResume(prefs.getString("pendingTurn", ""))) {
      EveApi current = observedApi;
      if (current != null) controls.execute(current::disconnectStream);
    }
  }

  void resumePendingTurn() {
    String pending = prefs.getString("pendingTurn", "");
    if (busy || recoveryAttempted || !configured() || !TurnRecovery.canResume(pending)) return;
    recoveryAttempted = true;
    turnActive = true;
    turnId = pending;
    liveState = "reconnecting";
    thought = "";
    task(
        "Laufenden Auftrag wieder verbinden …",
        api -> {
          JSONObject snapshot = api.json("GET", "/api/backend-turns/" + EveApi.path(pending), null);
          String id = snapshot.optString("conversation_id", conversationId);
          if (id.isEmpty() || id.equals("null")) id = conversationId;
          JSONObject history =
              id.isEmpty()
                  ? null
                  : api.json("GET", "/api/conversations/" + EveApi.path(id) + "?limit=200", null);
          main.post(
              () -> {
                if (history != null) applyHistory(history);
                outgoing = findTurnMessage(pending, "user");
                incoming = findTurnMessage(pending, "assistant");
                if (outgoing == null) {
                  outgoing =
                      new Message(
                          "user",
                          snapshot.optString(
                              "input_text", snapshot.optString("transcript", "Sprachnachricht …")));
                  outgoing.turnId = pending;
                  messages.add(outgoing);
                }
                if (incoming == null) {
                  incoming = new Message("assistant", "");
                  incoming.turnId = pending;
                  messages.add(incoming);
                }
                incoming.content = "";
                thought = "";
                online = true;
                connectedOnce = true;
                if ("failed".equals(snapshot.optString("status")))
                  prefs.edit().remove("pendingTurn").apply();
                changed();
              });
          if ("failed".equals(snapshot.optString("status")))
            throw new IOException(
                snapshot.optString("error", "EVE konnte den Auftrag nicht abschließen."));
          observe(api, "GET", TurnRecovery.streamPath(pending), null, null);
        });
  }

  private Message findTurnMessage(String id, String role) {
    for (int i = messages.size() - 1; i >= 0; i--) {
      Message m = messages.get(i);
      if (m.turnId.equals(id) && m.role.equals(role)) return m;
    }
    return null;
  }

  private void observe(EveApi api, String method, String path, String type, byte[] body)
      throws Exception {
    observedApi = api;
    try {
      api.stream(method, path, type, body, event -> main.post(() -> accept(event)));
    } finally {
      observedApi = null;
    }
  }

  String liveState = "idle", liveDetail = "", thought = "";
  final ScheduledExecutorService liveWorker = Executors.newSingleThreadScheduledExecutor();
  ScheduledFuture<?> livePoll;

  String thoughtPreview() {
    return ThoughtText.excerpt(thought);
  }

  void startLiveState() {
    if (livePoll != null && !livePoll.isCancelled()) return;
    livePoll =
        liveWorker.scheduleWithFixedDelay(
            () -> {
              if (observer == null || !turnActive || !configured()) return;
              String id = turnId;
              try {
                JSONObject state =
                    new EveApi(prefs.getString("url", ""), prefs.getString("token", ""), 2500)
                        .json("GET", "/api/state", null);
                main.post(
                    () -> {
                      if (observer == null || !turnActive || !id.equals(turnId)) return;
                      String next = state.optString("state", "thinking");
                      if (next.startsWith("tool:")
                          || next.startsWith("memory:")
                          || next.equals("thinking")
                          || next.equals("listening")) {
                        String detail =
                            state.optString("detail", next.substring(next.indexOf(':') + 1));
                        if (detail.isEmpty()) detail = next.substring(next.indexOf(':') + 1);
                        if (!next.equals(liveState) || !detail.equals(liveDetail)) {
                          liveState = next;
                          liveDetail = detail;
                          changed();
                        }
                      }
                    });
              } catch (Exception ignored) {
                /* Optional visual telemetry must not interrupt a chat. */
              }
            },
            0,
            700,
            TimeUnit.MILLISECONDS);
  }

  void stopLiveState() {
    if (livePoll != null) {
      livePoll.cancel(false);
      livePoll = null;
    }
  }

  Message incoming, outgoing;

  static final class Message {
    final String role;
    String content;
    String thought = "";
    String turnId = "";
    String id = "";
    final ArrayList<String> images = new ArrayList<>();

    Message(String role, String content) {
      this.role = role;
      this.content = content;
    }
  }

  interface Work {
    void run(EveApi api) throws Exception;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    prefs = getSharedPreferences("eve", MODE_PRIVATE);
    conversationId = prefs.getString("conversation", "");
    if (!prefs.getString("pendingTurn", "").isEmpty())
      error =
          "Die App wurde während einer Antwort beendet. Öffne den Verlauf zum Prüfen; es wurde"
              + " nichts erneut gesendet.";
  }

  boolean configured() {
    return !prefs.getString("url", "").isEmpty();
  }

  EveApi api() {
    return new EveApi(prefs.getString("url", ""), prefs.getString("token", ""));
  }

  void changed() {
    if (observer != null) observer.run();
  }

  void rememberConversation(String id) {
    conversationId = id;
    prefs.edit().putString("conversation", id).apply();
  }

  void task(String label, Work work) {
    if (busy) return;
    final EveApi connection;
    try {
      connection = api();
    } catch (Exception e) {
      error = e.getMessage();
      changed();
      return;
    }
    busy = true;
    status = label;
    error = "";
    changed();
    worker.execute(
        () -> {
          boolean interrupted = false;
          try {
            work.run(connection);
          } catch (Exception e) {
            interrupted = e instanceof IOException;
            main.post(
                () -> {
                  error = humanError(e);
                  status = "Bitte prüfen";
                });
          } finally {
            final boolean reconnect = interrupted;
            main.post(
                () -> {
                  busy = false;
                  turnActive = false;
                  cancellable = false;
                  incoming = null;
                  outgoing = null;
                  if (error.isEmpty() && !status.equals("Antwort abgebrochen"))
                    status = "Verbunden";
                  changed();
                  if (reconnect && observer != null) resumePendingTurn();
                });
          }
        });
  }

  static String humanError(Exception e) {
    if (e instanceof java.net.UnknownHostException)
      return "EVE-Adresse nicht erreichbar. Adresse und WLAN oder VPN prüfen.";
    if (e instanceof javax.net.ssl.SSLException)
      return "Das HTTPS-Zertifikat wird nicht vertraut. Bitte ein gültiges Zertifikat verwenden.";
    if (e instanceof java.net.SocketTimeoutException)
      return "EVE antwortet nicht rechtzeitig. Verlauf prüfen, bevor du erneut sendest.";
    if (e instanceof java.net.SocketException)
      return "Verbindung zu EVE unterbrochen. WLAN oder VPN prüfen und die App erneut öffnen.";
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  void connect() {
    task(
        "Verbinde mit EVE …",
        api -> {
          JSONObject list = api.json("GET", "/api/conversations?limit=100", null);
          JSONArray items = list.optJSONArray("conversations");
          String id = conversationId;
          boolean found = false;
          if (items != null)
            for (int i = 0; i < items.length(); i++)
              if (items.getJSONObject(i).optString("id").equals(id)) found = true;
          if (!found) id = list.optString("active_id", "");
          if (id.isEmpty() && items != null && items.length() > 0)
            id = items.getJSONObject(0).getString("id");
          JSONObject history =
              id.isEmpty()
                  ? null
                  : api.json("GET", "/api/conversations/" + EveApi.path(id) + "?limit=200", null);
          final String chosen = id;
          main.post(
              () -> {
                conversations = items == null ? new JSONArray() : items;
                online = true;
                connectedOnce = true;
                status = "Verbunden";
                if (history != null) applyHistory(history);
                else rememberConversation(chosen);
              });
        });
  }

  void applyHistory(JSONObject data) {
    JSONObject conv = data.optJSONObject("conversation");
    if (conv != null) {
      rememberConversation(conv.optString("id"));
      title = conv.optString("title", "Unterhaltung");
    }
    messages.clear();
    JSONArray items = data.optJSONArray("messages");
    if (items != null)
      for (int i = 0; i < items.length(); i++) {
        JSONObject m = items.optJSONObject(i);
        if (m == null) continue;
        String role = m.optString("role");
        if (!role.equals("assistant") && !role.equals("user")) continue;
        String text = m.optString("content");
        JSONArray attachments = m.optJSONArray("attachments");

        Message message = new Message(role, text);
        message.id = m.optString("id", "");
        message.images.addAll(ChatImages.sources(attachments));
        message.turnId = m.optString("turn_id", "");
        JSONObject metadata = m.optJSONObject("metadata");
        if (metadata != null) message.thought = metadata.optString("reasoning", "");
        messages.add(message);
      }
    if (data.optBoolean("has_more")) status = "Die letzten 200 Nachrichten · Verbunden";
  }

  void select(String id) {
    task(
        "Lade Gespräch …",
        api -> {
          JSONObject data =
              api.json("GET", "/api/conversations/" + EveApi.path(id) + "?limit=200", null);
          main.post(
              () -> {
                applyHistory(data);
                status = "Verbunden";
              });
        });
  }

  void create() {
    task(
        "Neues Gespräch …",
        api -> {
          JSONObject data =
              api.json(
                  "POST", "/api/conversations", new JSONObject().put("title", "Neue Unterhaltung"));
          JSONObject list = api.json("GET", "/api/conversations?limit=100", null);
          main.post(
              () -> {
                applyHistory(data);
                conversations = list.optJSONArray("conversations");
                status = "Verbunden";
              });
        });
  }

  void send(String text, List<String> images, byte[] voice) {
    if (busy) return;
    recoveryAttempted = false;
    turnActive = true;
    turnId = "";
    thought = "";
    liveState = "thinking";
    liveDetail = "";
    task(
        voice == null ? "EVE denkt …" : "EVE hört zu …",
        api -> {
          String id = conversationId;
          if (id.isEmpty()) {
            id =
                api.json(
                        "POST",
                        "/api/conversations",
                        new JSONObject().put("title", "Neue Unterhaltung"))
                    .getJSONObject("conversation")
                    .getString("id");
            final String newId = id;
            main.post(() -> rememberConversation(newId));
          }
          final String chosen = id;
          // Written before the POST: a process death must never trigger a repeated action.
          prefs.edit().putString("pendingTurn", "sending").commit();
          main.post(
              () -> {
                outgoing = new Message("user", voice == null ? text : "Sprachnachricht …");
                outgoing.images.addAll(images);
                messages.add(outgoing);
                incoming = new Message("assistant", "");
                messages.add(incoming);
                changed();
              });
          String path;
          byte[] body;
          String contentType;
          if (voice == null) {
            JSONObject payload =
                new JSONObject()
                    .put("text", text)
                    .put("images", new JSONArray(images))
                    .put("conversation_id", chosen)
                    .put("resume_conversation", true);
            body = payload.toString().getBytes(StandardCharsets.UTF_8);
            path = "/api/message/stream";
            contentType = "application/json";
          } else {
            body = voice;
            path =
                "/api/turn/stream?conversation_id="
                    + EveApi.path(chosen)
                    + "&resume_conversation=true";
            contentType = "audio/wav";
          }
          observe(api, "POST", path, contentType, body);
        });
  }

  void accept(JSONObject e) {
    switch (e.optString("type")) {
      case "accepted":
        if (liveState.equals("reconnecting")) liveState = "thinking";
        turnId = e.optString("turn_id");
        if (outgoing != null) outgoing.turnId = turnId;
        if (incoming != null) incoming.turnId = turnId;
        cancellable = e.optBoolean("cancellable");
        prefs.edit().putString("pendingTurn", turnId).apply();
        if (!e.optString("conversation_id").isEmpty())
          rememberConversation(e.optString("conversation_id"));
        break;
      case "status":
        cancellable = e.optBoolean("cancellable") && !e.optBoolean("cancel_requested");
        break;
      case "input":
      case "transcript":
        if (outgoing != null) outgoing.content = e.optString("content");
        break;
      case "reasoning_delta":
        thought += e.optString("content");
        if (thought.length() > 2000) thought = thought.substring(thought.length() - 2000);
        if (incoming != null) incoming.thought = thought;
        liveState = "thinking";
        break;
      case "preview_reset":
        liveState = "thinking";
        if (incoming != null) incoming.content = "";
        break;
      case "delta":
      case "preview_delta":
        liveState = "answering";
        if (incoming != null) incoming.content += e.optString("content");
        break;
      case "audio":
        if (audio != null && prefs.getBoolean("voice", true))
          audio.accept(e.optString("audio_wav_base64"));
        break;
      case "complete":
        turnActive = false;
        JSONObject result = e.optJSONObject("result");
        if (result != null) {
          if (incoming != null) {
            incoming.content = result.optString("answer", incoming.content);
            incoming.id = result.optString("assistant_message_id", "");
          }
          if (!result.optString("conversation_id").isEmpty())
            rememberConversation(result.optString("conversation_id"));
        }
        prefs.edit().remove("pendingTurn").apply();
        status = "Verbunden";
        refreshTurnImages(turnId, conversationId);
        break;
      case "cancelled":
        turnActive = false;
        prefs.edit().remove("pendingTurn").apply();
        status = "Antwort abgebrochen";
        break;
    }
    changed();
  }

  private void refreshTurnImages(String turn, String conversation) {
    if (conversation.isEmpty()) return;
    final EveApi connection = api();
    imageLoader.worker.execute(
        () -> {
          try {
            JSONArray rows =
                connection
                    .json(
                        "GET",
                        "/api/conversations/" + EveApi.path(conversation) + "?limit=200",
                        null)
                    .optJSONArray("messages");
            main.post(
                () -> {
                  if (!conversation.equals(conversationId) || rows == null) return;
                  boolean updated = false;
                  for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    ArrayList<String> sources = ChatImages.sources(row.optJSONArray("attachments"));
                    if (sources.isEmpty()) continue;
                    for (Message m : messages) {
                      boolean sameId = !m.id.isEmpty() && m.id.equals(row.optString("id"));
                      boolean sameTurn =
                          m.turnId.equals(turn)
                              && turn.equals(row.optString("turn_id"))
                              && m.role.equals(row.optString("role"));
                      if ((sameId || sameTurn) && !m.images.equals(sources)) {
                        m.images.clear();
                        m.images.addAll(sources);
                        updated = true;
                      }
                    }
                  }
                  if (updated) changed();
                });
          } catch (Exception ignored) {
            /* Existing inline previews remain; history can be refreshed later. */
          }
        });
  }

  void cancel() {
    if (turnId.isEmpty() || !cancellable) return;
    cancellable = false;
    status = "Abbruch angefragt …";
    changed();
    controls.execute(
        () -> {
          try {
            api()
                .json(
                    "POST",
                    "/api/backend-turns/" + EveApi.path(turnId) + "/cancel",
                    new JSONObject());
          } catch (Exception e) {
            main.post(
                () -> {
                  error = humanError(e);
                  changed();
                });
          }
        });
  }
}
