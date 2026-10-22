package de.eve.mobile;

import android.graphics.*;
import android.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Small memory cache, bounded image decoding, and independent media I/O. */
final class ChatImageLoader {
  final EveApp app;
  final ExecutorService worker = Executors.newFixedThreadPool(2);
  private final LruCache<String, Bitmap> cache =
      new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getAllocationByteCount();
        }
      };

  ChatImageLoader(EveApp app) {
    this.app = app;
  }

  void load(String source, int maxEdge, Consumer<Bitmap> done, Consumer<String> failed) {
    String address = app.prefs.getString("url", ""), token = app.prefs.getString("token", "");
    worker.execute(
        () -> {
          try {
            byte[] digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        (address + "\n" + token + "\n" + source + "\n" + maxEdge)
                            .getBytes(StandardCharsets.UTF_8));
            String key = Base64.encodeToString(digest, Base64.NO_WRAP);
            Bitmap bitmap = cache.get(key);
            if (bitmap == null) {
              byte[] bytes;
              if (source.startsWith("data:image/") && source.contains(";base64,")) {
                if (source.length() > 28 * 1024 * 1024)
                  throw new java.io.IOException("Bild ist zu groß.");
                bytes = Base64.decode(source.substring(source.indexOf(',') + 1), Base64.DEFAULT);
              } else bytes = new EveApi(address, token, 15000).image(source);
              BitmapFactory.Options bounds = new BitmapFactory.Options();
              bounds.inJustDecodeBounds = true;
              BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
              if (bounds.outWidth < 1 || bounds.outHeight < 1)
                throw new java.io.IOException("Bildformat wird nicht unterstützt.");
              BitmapFactory.Options options = new BitmapFactory.Options();
              while (Math.max(bounds.outWidth, bounds.outHeight) / Math.max(1, options.inSampleSize)
                  > maxEdge) options.inSampleSize = Math.max(1, options.inSampleSize) * 2;
              bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
              if (bitmap == null)
                throw new java.io.IOException("Bild konnte nicht geladen werden.");
              cache.put(key, bitmap);
            }
            Bitmap ready = bitmap;
            app.main.post(() -> done.accept(ready));
          } catch (Exception e) {
            app.main.post(() -> failed.accept(EveApp.humanError(e)));
          }
        });
  }
}
