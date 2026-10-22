package de.eve.mobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Transport only: no prompt modifications and no automatic turn retries. */
public final class EveApi {
  private final String base, token;
  private final int readTimeout;
  private volatile HttpURLConnection streamingConnection;

  void disconnectStream() {
    HttpURLConnection connection = streamingConnection;
    if (connection != null) connection.disconnect();
  }

  public interface Events {
    void event(JSONObject event) throws Exception;
  }

  public EveApi(String address, String token) {
    this(address, token, 210000);
  }

  EveApi(String address, String token, int readTimeout) {
    this.readTimeout = readTimeout;
    URI uri;
    try {
      uri = URI.create(address.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("Ungültige EVE-Adresse");
    }
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException(
          "Bitte eine HTTP(S)-Adresse ohne Zugangsdaten oder Parameter eingeben.");
    this.base = uri.toString().replaceAll("/+$", "");
    this.token = token.trim();
  }

  public static String path(String id) {
    return encode(id);
  }

  private static String encode(String id) {
    try {
      return URLEncoder.encode(id, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    return out.toByteArray();
  }

  private HttpURLConnection open(String method, String path, String type, byte[] body)
      throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
    c.setInstanceFollowRedirects(false);
    c.setUseCaches(false);
    c.setConnectTimeout(12000);
    c.setReadTimeout(readTimeout);
    c.setRequestMethod(method);
    c.setRequestProperty("Accept", "application/json, application/x-ndjson");
    if (!token.isEmpty()) c.setRequestProperty("X-EVE-Token", token);
    try {
      if (body != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", type);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = c.getOutputStream()) {
          out.write(body);
        }
      }
      int code = c.getResponseCode();
      if (code < 200 || code >= 300) {
        String detail = "";
        InputStream error = c.getErrorStream();
        if (error != null)
          try (InputStream in = error) {
            detail = new String(read(in), StandardCharsets.UTF_8);
          }
        if (code == 401 || code == 403)
          throw new IOException("Zugriff verweigert. Bitte den EVE-Web-Zugriffstoken prüfen.");
        try {
          detail = new JSONObject(detail).optString("error", "");
        } catch (JSONException ignored) {
          detail = "";
        }
        throw new IOException("EVE meldet HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
      }
      return c;
    } catch (Exception e) {
      c.disconnect();
      throw e;
    }
  }

  byte[] image(String path) throws Exception {
    if (!ChatImages.assetPath(path)) throw new IOException("Ungültiger Bildanhang.");
    HttpURLConnection c = open("GET", path, null, null);
    try (InputStream in = c.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) {
        if (out.size() + n > 20 * 1024 * 1024) throw new IOException("Bild ist zu groß.");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    } finally {
      c.disconnect();
    }
  }

  public JSONObject json(String method, String path, JSONObject body) throws Exception {
    HttpURLConnection c =
        open(
            method,
            path,
            "application/json",
            body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8));
    try (InputStream in = c.getInputStream()) {
      return new JSONObject(new String(read(in), StandardCharsets.UTF_8));
    } finally {
      c.disconnect();
    }
  }

  public void stream(String method, String path, String contentType, byte[] body, Events sink)
      throws Exception {
    HttpURLConnection c = open(method, path, contentType, body);
    streamingConnection = c;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      boolean terminal = false;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) continue;
        JSONObject event = new JSONObject(line);
        String kind = event.optString("type");
        if (kind.equals("error"))
          throw new IOException(
              event.optString("error", "EVE konnte die Anfrage nicht abschließen."));
        if (kind.equals("complete")) {
          JSONObject result = event.optJSONObject("result");
          if (result == null || !(result.opt("answer") instanceof String))
            throw new IOException(
                "EVE lieferte keinen gültigen Abschluss. Bitte den Verlauf prüfen.");
        }
        sink.event(event);
        if (kind.equals("complete") || kind.equals("cancelled")) {
          terminal = true;
          break;
        }
      }
      if (!terminal)
        throw new IOException(
            "Verbindung vor Abschluss unterbrochen. Verlauf prüfen, bevor du erneut sendest.");
    } finally {
      streamingConnection = null;
      c.disconnect();
    }
  }
}
