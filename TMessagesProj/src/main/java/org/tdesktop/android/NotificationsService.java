/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tdesktop.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.tdesktop.messenger.FileLog;
import org.tdesktop.ui.ApplicationLoader;

public class NotificationsService extends Service {

    @Override
    public void onCreate() {
        FileLog.e("tdesktop", "service started");
        ApplicationLoader.postInitApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        FileLog.e("tdesktop", "service destroyed");

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", MODE_PRIVATE);
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.tdesktop.start");
            sendBroadcast(intent);
        }
    }
}
