package de.eve.mobile;

import java.util.ArrayList;
import org.json.*;

/** Conversation image paths are rebuilt from asset IDs, never arbitrary authenticated URLs. */
final class ChatImages {
  static ArrayList<String> sources(JSONArray attachments) {
    ArrayList<String> result = new ArrayList<>();
    if (attachments == null) return result;
    for (int i = 0; i < attachments.length(); i++) {
      JSONObject a = attachments.optJSONObject(i);
      if (a == null || !a.optString("media_type").startsWith("image/")) continue;
      String id = a.optString("id", "");
      if (!id.isEmpty() && !id.equals("null"))
        result.add("/api/conversation-assets/" + EveApi.path(id));
      else {
        String path = a.optString("url", "");
        if (assetPath(path)) result.add(path);
      }
    }
    return result;
  }

  static boolean assetPath(String path) {
    return path != null
        && path.matches("/api/conversation-assets/[A-Za-z0-9%._~-]+")
        && !path.endsWith("/..")
        && !path.endsWith("/.");
  }
}
