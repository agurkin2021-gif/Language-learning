package ru.spanish.learning;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Three-page horizontal strip. Adjacent cards enter the viewport while the user's
 * finger is moving; release either snaps to the next card or springs back.
 * Only three actual card views exist at a time, regardless of vocabulary size.
 */
final class CardSwipePager extends FrameLayout {
    interface Listener {
        void onTap();
        void onDragStarted();
        void onOffset(float dx);
        void onPageChanged(int delta);
    }

    // Mutable slots are rotated at the end of an animation. The same three
    // views and WebViews remain attached; no full Activity recreation is needed.
    private View previous;
    private View current;
    private View next;
    private final Listener listener;
    private final View touchSurface;
    private final boolean canSwipe;
    private float downX, downY, offset;
    private boolean dragging, settling;
    private ValueAnimator animation;

    CardSwipePager(Context context, View previous, View current, View next,
                   boolean canSwipe, Listener listener) {
        super(context);
        this.previous = previous;
        this.current = current;
        this.next = next;
        this.canSwipe = canSwipe;
        this.listener = listener;
        setClipChildren(true);
        setClipToPadding(true);
        previous.setTag("card-swipe-previous");
        current.setTag("card-swipe-front");
        next.setTag("card-swipe-next");
        addView(previous, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(next, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(current, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        touchSurface = new View(context);
        touchSurface.setTag("card-swipe-surface");
        touchSurface.setElevation(dp(8));
        touchSurface.setContentDescription(
                "Нажмите для перевода. Пролистайте влево для следующей или вправо для предыдущей карточки");
        touchSurface.setOnClickListener(view -> listener.onTap());
        touchSurface.setOnTouchListener(this::handleTouch);
        addView(touchSurface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float range() {
        return getWidth() + dp(12);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        showOffset(offset);
    }

    private void showOffset(float dx) {
        offset = dx;
        float stride = range();
        previous.setTranslationX(dx - stride);
        current.setTranslationX(dx);
        next.setTranslationX(dx + stride);
        listener.onOffset(dx);
    }

    private boolean handleTouch(View v, MotionEvent event) {
        if (settling) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && canSwipe && Math.abs(dx) > dp(9)
                        && Math.abs(dx) > Math.abs(dy) * 1.18f) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    listener.onDragStarted();
                }
                if (dragging) {
                    showOffset(Math.max(-range(), Math.min(range(), dx)));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dragging) {
                    int threshold = Math.max(dp(48), Math.round(getWidth() * .18f));
                    if (Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.1f)
                        settle(dx < 0 ? 1 : -1);
                    else settle(0);
                } else if (Math.abs(dx) <= dp(14) && Math.abs(dy) <= dp(14)) {
                    v.performClick();
                }
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (dragging) settle(0);
                dragging = false;
                return true;
            default: return true;
        }
    }

    private void settle(int delta) {
        settling = true;
        if (animation != null) animation.cancel();
        final float target = delta == 0 ? 0f : (delta > 0 ? -range() : range());
        animation = ValueAnimator.ofFloat(offset, target);
        animation.setDuration(delta == 0 ? 170 : 205);
        animation.setInterpolator(new DecelerateInterpolator(1.7f));
        animation.addUpdateListener(value -> showOffset((float) value.getAnimatedValue()));
        animation.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animator) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animator) {
                settling = false;
                animation = null;
                if (cancelled) return;
                if (delta == 0) {
                    showOffset(0);
                } else {
                    // At the completed animation the incoming page is already
                    // exactly at x=0. Recycle only the page far outside the viewport.
                    if (delta > 0) {
                        View recycled = previous;
                        previous = current;
                        current = next;
                        next = recycled;
                    } else {
                        View recycled = next;
                        next = current;
                        current = previous;
                        previous = recycled;
                    }
                    previous.setTag("card-swipe-previous");
                    current.setTag("card-swipe-front");
                    next.setTag("card-swipe-next");
                    showOffset(0);
                    // Rebinding the off-screen page occurs after the stable
                    // visible page has arrived. Its WebView never reloads.
                    listener.onPageChanged(delta);
                }
            }
        });
        animation.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        super.onDetachedFromWindow();
    }
}
