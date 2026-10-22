package de.eve.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/** A subtle rotating contour driven by real activity, stopped off screen. */
final class SignalFrame extends FrameLayout {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Matrix rotation = new Matrix();
  private final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
  private final float radius;
  private int signal = Ui.CYAN;
  private boolean active, motion = true;
  private float phase;

  SignalFrame(Context c, int corner) {
    super(c);
    radius = Ui.dp(c, corner);
    setWillNotDraw(false);
    setBackground(Ui.surface(c, Ui.SURFACE, corner, Ui.BORDER));
    animator.setDuration(2400);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(
        a -> {
          phase = (float) a.getAnimatedValue();
          invalidate();
        });
  }

  void signal(int color, boolean on, boolean animate) {
    if (signal == color && active == on && motion == animate) return;
    signal = color;
    active = on;
    motion = animate;
    sync();
    invalidate();
  }

  private void sync() {
    boolean run =
        active
            && motion
            && isAttachedToWindow()
            && isShown()
            && ValueAnimator.areAnimatorsEnabled();
    if (run && !animator.isStarted()) animator.start();
    else if (!run && animator.isStarted()) animator.cancel();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    sync();
  }

  @Override
  protected void onDetachedFromWindow() {
    animator.cancel();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(View changed, int visibility) {
    super.onVisibilityChanged(changed, visibility);
    if (animator != null) sync();
  }

  @Override
  protected void onWindowVisibilityChanged(int v) {
    super.onWindowVisibilityChanged(v);
    if (animator != null) sync();
  }

  @Override
  protected void dispatchDraw(Canvas c) {
    super.dispatchDraw(c);
    if (!active) return;
    float inset = Ui.dp(getContext(), 1);
    bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
    paint.setStyle(Paint.Style.STROKE);
    paint.setShader(null);
    paint.setColor((signal & 0x00ffffff) | 0x22000000);
    paint.setStrokeWidth(Ui.dp(getContext(), 7));
    c.drawRoundRect(bounds, radius, radius, paint);
    SweepGradient shader =
        new SweepGradient(
            getWidth() / 2f,
            getHeight() / 2f,
            new int[] {(signal & 0xffffff) | 0x33000000, signal, (signal & 0xffffff) | 0x33000000},
            new float[] {0, .5f, 1});
    rotation.setRotate(phase * 360, getWidth() / 2f, getHeight() / 2f);
    shader.setLocalMatrix(rotation);
    paint.setShader(shader);
    paint.setStrokeWidth(Ui.dp(getContext(), 1.3f));
    c.drawRoundRect(bounds, radius, radius, paint);
    paint.setShader(null);
  }
}
