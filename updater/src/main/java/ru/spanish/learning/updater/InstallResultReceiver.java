package ru.spanish.learning.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        UpdaterActivity.handleInstallResult(context, intent);
    }
}
