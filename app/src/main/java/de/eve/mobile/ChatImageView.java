package de.eve.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Lazy inline preview with a pinch-to-zoom viewer. */
final class ChatImageView extends LinearLayout {
  final EveApp app;
  final String source;
  final ImageView image;
  final TextView status;
  boolean loading, loaded;
  final android.graphics.Rect visible = new android.graphics.Rect();
  final ViewTreeObserver.OnScrollChangedListener scrolled = this::maybeLoad;

  ChatImageView(Context c, String source) {
    super(c);
    this.source = source;
    app = (EveApp) c.getApplicationContext();
    setOrientation(VERTICAL);
    setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 10));
    image = new ImageView(c);
    image.setContentDescription("Bild im Chat · antippen zum Vergrößern");
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setBackground(Ui.surface(c, Ui.DEEP, 14, Ui.BORDER));
    addView(image, new LinearLayout.LayoutParams(-1, Ui.dp(c, 200)));
    image.setOnClickListener(
        v -> {
          if (loaded) open();
          else maybeLoad();
        });
    status = Ui.text(c, "Bild wird geladen …", 12, Ui.MUTED);
    status.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), 0, 0);
    addView(status);
    status.setOnClickListener(v -> maybeLoad());
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnScrollChangedListener(scrolled);
    post(this::maybeLoad);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (getViewTreeObserver().isAlive())
      getViewTreeObserver().removeOnScrollChangedListener(scrolled);
    super.onDetachedFromWindow();
  }

  void maybeLoad() {
    if (loading || loaded || !isAttachedToWindow() || !getLocalVisibleRect(visible)) return;
    loading = true;
    app.imageLoader.load(
        source,
        1024,
        bitmap -> {
          loading = false;
          if (!isAttachedToWindow()) return;
          loaded = true;
          image.setImageBitmap(bitmap);
          status.setText("Antippen zum Vergrößern");
        },
        error -> {
          loading = false;
          if (isAttachedToWindow()) status.setText("Bild nicht geladen · zum Wiederholen tippen");
        });
  }

  void open() {
    Dialog dialog = new Dialog(getContext(), android.R.style.Theme_Material_NoActionBar);
    LinearLayout page = Ui.column(getContext());
    page.setBackgroundColor(Ui.DEEP);
    LinearLayout bar = Ui.row(getContext());
    TextView title = Ui.text(getContext(), "Bild", 18, Ui.TEXT);
    title.setPadding(Ui.dp(getContext(), 16), 0, 0, 0);
    bar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 56), 1));
    bar.addView(
        Ui.icon(getContext(), R.drawable.ic_close, "Bild schließen", dialog::dismiss),
        new LinearLayout.LayoutParams(Ui.dp(getContext(), 56), Ui.dp(getContext(), 56)));
    page.addView(bar);
    ZoomImage zoom = new ZoomImage(getContext());
    zoom.setImageDrawable(image.getDrawable());
    page.addView(zoom, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(page);
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
    app.imageLoader.load(
        source,
        2048,
        bitmap -> {
          if (dialog.isShowing()) zoom.setImageBitmap(bitmap);
        },
        error -> {});
  }

  static final class ZoomImage extends ImageView {
    final Matrix matrix = new Matrix();
    final ScaleGestureDetector scale;
    float lastX, lastY;
    boolean placed;

    ZoomImage(Context c) {
      super(c);
      setScaleType(ScaleType.MATRIX);
      scale =
          new ScaleGestureDetector(
              c,
              new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                  float current =
                      (float) Math.hypot(matrixValue(Matrix.MSCALE_X), matrixValue(Matrix.MSKEW_Y));
                  if (current > 0) {
                    float desired = current * d.getScaleFactor();
                    if (desired > .02f && desired < 12)
                      matrix.postScale(
                          d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                    setImageMatrix(matrix);
                  }
                  return true;
                }
              });
    }

    float matrixValue(int index) {
      float[] values = new float[9];
      matrix.getValues(values);
      return values[index];
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
      super.setImageBitmap(bitmap);
      placed = false;
      post(this::fit);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      super.onSizeChanged(w, h, oldW, oldH);
      placed = false;
      fit();
    }

    void fit() {
      if (placed || getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
      float w = getDrawable().getIntrinsicWidth(), h = getDrawable().getIntrinsicHeight();
      float s = Math.min(getWidth() / w, getHeight() / h);
      matrix.setScale(s, s);
      matrix.postTranslate((getWidth() - w * s) / 2, (getHeight() - h * s) / 2);
      setImageMatrix(matrix);
      placed = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      scale.onTouchEvent(e);
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        lastX = e.getX();
        lastY = e.getY();
      } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
          && !scale.isInProgress()
          && e.getPointerCount() == 1) {
        matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
        setImageMatrix(matrix);
        lastX = e.getX();
        lastY = e.getY();
      } else if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
        int index = e.getActionIndex() == 0 ? 1 : 0;
        if (index < e.getPointerCount()) {
          lastX = e.getX(index);
          lastY = e.getY(index);
        }
      }
      return true;
    }
  }
}
