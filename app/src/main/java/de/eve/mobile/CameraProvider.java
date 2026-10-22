package de.eve.mobile;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** One temporary photo, shared only with the system camera via URI permission. */
public final class CameraProvider extends ContentProvider {
  static final Uri URI = Uri.parse("content://de.eve.mobile.camera/photo");

  @Override
  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) throws FileNotFoundException {
    if (!URI.equals(uri)) throw new FileNotFoundException();
    return new File(getContext().getCacheDir(), "camera.jpg");
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public String getType(Uri uri) {
    return "image/jpeg";
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    MatrixCursor c =
        new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
    try {
      c.addRow(new Object[] {"eve-photo.jpg", file(uri).length()});
    } catch (Exception ignored) {
    }
    return c;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    return 0;
  }
}
