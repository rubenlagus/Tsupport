/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tsupport.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.tsupport.messenger.FileLog;
import org.tsupport.ui.ApplicationLoader;

public class NotificationsService extends Service {

    @Override
    public void onCreate() {
        FileLog.e("tsupport", "service started");
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
        FileLog.e("tsupport", "service destroyed");

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", MODE_PRIVATE);
        if (preferences.getBoolean("pushService", false)) {
            Intent intent = new Intent("org.tsupport.start");
            sendBroadcast(intent);
        }
    }
}
