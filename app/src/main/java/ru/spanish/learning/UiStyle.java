package ru.spanish.learning;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;

/**
 * Shared, accessible colors and rounded controls for both app themes.
 * Press feedback uses the platform ripple, not a static decorative effect.
 */
final class UiStyle {
    enum Tone { PRIMARY, SECONDARY, OUTLINE, TAB, SELECTED, ICON }

    final boolean dark;
    final int background;
    final int foreground;
    final int muted;
    final int surface;
    final int soft;
    final int outline;
    final int accent;
    final int onAccent;
    final int danger;
    final int dangerSoft;
    final int rippleTint;
    private final Context context;

    UiStyle(Context context, boolean dark) {
        this.context = context;
        this.dark = dark;
        background = dark ? 0xFF0F1A19 : 0xFFF3F7F5;
        foreground = dark ? 0xFFF0F8F6 : 0xFF18352F;
        muted = dark ? 0xFFA8C0BA : 0xFF566F69;
        surface = dark ? 0xFF1A2B28 : Color.WHITE;
        soft = dark ? 0xFF253A35 : 0xFFEAF3F0;
        outline = dark ? 0xFF3A5750 : 0xFFD2E2DC;
        accent = dark ? 0xFF79E3CF : 0xFF087F72;
        onAccent = dark ? 0xFF09312A : Color.WHITE;
        danger = dark ? 0xFFFF8991 : 0xFFC52B40;
        dangerSoft = dark ? 0xFF472B32 : 0xFFFFE9EC;
        rippleTint = dark ? 0x5579E3CF : 0x3300877A;
    }

    int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable rounded(int fill, int radius, int border) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) bg.setStroke(dp(1), border);
        return bg;
    }

    GradientDrawable cardSurface() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xFF223A34, 0xFF172824}
                        : new int[]{0xFFFFFFFF, 0xFFF6FBF9});
        bg.setCornerRadius(dp(25));
        bg.setStroke(dp(1), outline);
        return bg;
    }

    Drawable ripple(int fill, int radius, int border) {
        StateListDrawable layers = new StateListDrawable();
        layers.addState(new int[]{-android.R.attr.state_enabled},
                rounded(dark ? 0xFF24332F : 0xFFEEF2F0, radius, outline));
        layers.addState(new int[]{}, rounded(fill, radius, border));
        return new RippleDrawable(ColorStateList.valueOf(rippleTint), layers,
                rounded(Color.WHITE, radius, Color.TRANSPARENT));
    }

    void style(Button button, Tone tone) {
        int fill = soft;
        int border = Color.TRANSPARENT;
        int label = foreground;
        int radius = 15;
        switch (tone) {
            case PRIMARY:
            case SELECTED:
                fill = accent;
                label = onAccent;
                break;
            case OUTLINE:
                fill = surface;
                border = outline;
                break;
            case TAB:
                fill = soft;
                break;
            case ICON:
                fill = surface;
                border = outline;
                radius = 17;
                break;
            case SECONDARY:
            default:
                break;
        }
        button.setAllCaps(false);
        button.setBackground(ripple(fill, radius, border));
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{muted, label}));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(tone == Tone.ICON ? 23 : tone == Tone.PRIMARY ? 16 : 14);
        button.setLetterSpacing(0.005f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(9), dp(12), dp(9));
        button.setMinHeight(dp(50));
        button.setMinimumHeight(dp(50));
        button.setElevation(dp(tone == Tone.PRIMARY || tone == Tone.SELECTED ? 3 : 1));
        button.setStateListAnimator(null);
    }

    void styleInput(EditText input) {
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, rounded(surface, 14, accent));
        state.addState(new int[]{}, rounded(surface, 14, outline));
        input.setBackground(state);
        input.setTextColor(foreground);
        input.setHintTextColor(muted);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setMinimumHeight(dp(52));
    }
}
