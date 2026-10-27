package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local display preference for the user's own ChatGPT account.
 * It neither purchases a plan nor changes the server's account entitlements.
 * No Subscription Sharing or paid OpenAI API is used by this class.
 */
public final class ChatGptPlanPreference {
    public static final String FREE = "free";
    public static final String PLUS = "plus";
    private static final String PREFS = "chatgpt_plan_preference";
    private static final String KEY = "selected_account_plan";

    private ChatGptPlanPreference() {}

    public static String selected(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return PLUS.equals(prefs.getString(KEY, FREE)) ? PLUS : FREE;
    }

    public static int selectedIndex(Context context) {
        return PLUS.equals(selected(context)) ? 1 : 0;
    }

    public static void select(Context context, String plan) {
        if (!FREE.equals(plan) && !PLUS.equals(plan))
            throw new IllegalArgumentException("Неизвестный тариф ChatGPT");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, plan).apply();
    }

    public static String label(Context context) {
        return PLUS.equals(selected(context)) ? "Plus ($20/мес.)" : "Бесплатный";
    }

    /** Always the supported official consumer UI, never Subscription Sharing. */
    public static String officialChatUrl() {
        return "https://chatgpt.com/";
    }
}
