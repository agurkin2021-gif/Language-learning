package ru.spanish.learning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives only the explicit PendingIntent created for this app's install session. */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AppUpdater.handleInstallResult(context, intent);
    }
}
