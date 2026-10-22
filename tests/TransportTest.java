package de.eve.mobile;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public final class TransportTest {
  static void check(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  static void reply(HttpExchange e, int code, String text) throws IOException {
    byte[] b = text.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(code, b.length);
    e.getResponseBody().write(b);
    e.close();
  }

  public static void main(String[] args) throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/conversations",
        e -> {
          check(
              "secret-test".equals(e.getRequestHeaders().getFirst("X-EVE-Token")),
              "Web token missing");
          reply(
              e,
              200,
              "{\"conversations\":[{\"id\":\"c1\",\"title\":\"Grüezi 🌙\"}],\"active_id\":\"c1\"}");
        });
    server.createContext(
        "/api/message/stream",
        e -> {
          requests.incrementAndGet();
          try {
            JSONObject body =
                new JSONObject(new String(EveApi.read(e.getRequestBody()), StandardCharsets.UTF_8));
            check(body.getString("text").equals("Licht an 🌙"), "User text changed");
            check(body.getBoolean("resume_conversation"), "Explicit conversation resume missing");
            check(body.getJSONArray("images").length() == 1, "Image lost");
            reply(
                e,
                200,
                "{\"type\":\"accepted\",\"turn_id\":\"t1\"}\n\n"
                    + "{\"type\":\"delta\",\"content\":\"Erledigt 🌙\"}\n"
                    + "{\"type\":\"complete\",\"result\":{\"answer\":\"Erledigt 🌙\"}}");
          } catch (JSONException ex) {
            throw new RuntimeException(ex);
          }
        });
    server.createContext(
        "/truncated",
        e -> {
          requests.incrementAndGet();
          reply(
              e,
              200,
              "{\"type\":\"accepted\"}\n{\"type\":\"delta\",\"content\":\"Teilantwort\"}\n");
        });
    server.createContext(
        "/failed",
        e -> {
          requests.incrementAndGet();
          reply(e, 200, "{\"type\":\"error\",\"error\":\"Gerät antwortet nicht\"}\n");
        });
    server.createContext(
        "/invalid-complete", e -> reply(e, 200, "{\"type\":\"complete\",\"result\":null}\n"));
    server.createContext("/unauthorized", e -> reply(e, 401, "{\"error\":\"denied\"}"));
    server.createContext("/cancelled", e -> reply(e, 200, "{\"type\":\"cancelled\"}\n"));
    server.createContext(
        "/redirect",
        e -> {
          e.getResponseHeaders().set("Location", "/api/conversations");
          reply(e, 302, "");
        });
    server.start();
    try {
      EveApi api =
          new EveApi("http://127.0.0.1:" + server.getAddress().getPort() + "/", "secret-test");
      check(
          api.json("GET", "/api/conversations", null)
              .getJSONArray("conversations")
              .getJSONObject(0)
              .getString("title")
              .equals("Grüezi 🌙"),
          "UTF-8 response changed");
      ArrayList<String> events = new ArrayList<>();
      JSONObject message =
          new JSONObject()
              .put("text", "Licht an 🌙")
              .put("resume_conversation", true)
              .put("conversation_id", "c1")
              .put("images", new JSONArray().put("data:image/jpeg;base64,/9j/"));
      api.stream(
          "POST",
          "/api/message/stream",
          "application/json",
          message.toString().getBytes(StandardCharsets.UTF_8),
          e -> events.add(e.getString("type")));
      check(
          events.equals(Arrays.asList("accepted", "delta", "complete")),
          "Stream order or final unterminated line lost");
      try {
        api.stream("POST", "/truncated", "application/json", new byte[] {123, 125}, e -> {});
        throw new AssertionError("Truncation accepted");
      } catch (IOException expected) {
        check(expected.getMessage().contains("Verlauf"), "Missing recovery advice");
      }
      check(requests.get() == 2, "A turn was automatically retried");
      try {
        api.stream("POST", "/failed", "application/json", new byte[] {123, 125}, e -> {});
        throw new AssertionError("Server error ignored");
      } catch (IOException expected) {
        check(expected.getMessage().contains("Gerät"), "Server error lost");
      }
      check(requests.get() == 3, "Failed turn retried");
      try {
        api.stream("GET", "/invalid-complete", null, null, e -> {});
        throw new AssertionError("Invalid completion accepted");
      } catch (IOException expected) {
        check(expected.getMessage().contains("Abschluss"), "Invalid completion not reported");
      }
      try {
        api.json("GET", "/unauthorized", null);
        throw new AssertionError("401 accepted");
      } catch (IOException expected) {
        check(expected.getMessage().contains("Zugriffstoken"), "Wrong auth error");
      }
      try {
        api.json("GET", "/redirect", null);
        throw new AssertionError("Redirect followed");
      } catch (IOException expected) {
        check(expected.getMessage().contains("302"), "Redirect not rejected");
      }
      api.stream(
          "GET",
          "/cancelled",
          null,
          null,
          e -> check(e.getString("type").equals("cancelled"), "Cancellation lost"));
      check(EveApi.path("a /?ü").equals("a%20%2F%3F%C3%BC"), "Unsafe path encoding");
      for (String invalid :
          new String[] {
            "file:///tmp/eve",
            "https://user:pass@example.com",
            "https://eve/#token",
            "https://eve/?token=abc",
            "eve.local"
          }) {
        try {
          new EveApi(invalid, "");
          throw new AssertionError("Invalid URL accepted: " + invalid);
        } catch (IllegalArgumentException expected) {
        }
      }
      System.out.println(
          "PASS: UTF-8, auth, images, stream framing, truncation, no retries, server errors,"
              + " redirects, cancellation and URL validation");
    } finally {
      server.stop(0);
    }
  }
}
