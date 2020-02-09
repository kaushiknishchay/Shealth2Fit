package com.shealth2fit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import static android.content.Context.ALARM_SERVICE;

public class SyncBootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
      // Set the alarm here.
      Context mContext = BaseApplication.getAppContext();

      Intent serviceIntent = new Intent(mContext, SyncService.class);
      PendingIntent pendingServiceIntent = PendingIntent.getService(mContext, 2222, serviceIntent,
       PendingIntent.FLAG_CANCEL_CURRENT);

      AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);

      am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
       SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_DAY,
       AlarmManager.INTERVAL_HALF_DAY, pendingServiceIntent);
    }
  }
}
