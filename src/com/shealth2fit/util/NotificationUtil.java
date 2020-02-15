package com.shealth2fit.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.shealth2fit.MainActivity;
import com.shealth2fit.R;

public class NotificationUtil {

  public static final String SYNC_WORKER_CHANNEL_ID = "Sync Service";
  public static final int SYNC_WORKER_NOTIFICATION_ID = 12987;


  public static void createNotificationChannel(Context mContext) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

      CharSequence name = mContext.getString(R.string.channel_name);
      String description = mContext.getString(R.string.channel_description);

      int importance = NotificationManager.IMPORTANCE_LOW;

      NotificationChannel channel = new NotificationChannel(SYNC_WORKER_CHANNEL_ID, name, importance);

      channel.setDescription(description);

      NotificationManager notificationManager = mContext.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  public static NotificationCompat.Builder sendNotification(Context mContext, String title, String content, String channelId) {
    return sendNotification(mContext, title, content, channelId, false);
  }

  public static NotificationCompat.Builder sendNotification(Context mContext, String title, String content, String channelId, boolean isOngoing) {

    NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, channelId)
     .setSmallIcon(R.mipmap.ic_launcher)
     .setContentTitle(title)
     .setPriority(NotificationCompat.PRIORITY_LOW)
     .setContentText(content)
     .setOngoing(isOngoing);


    Intent targetIntent = new Intent(mContext, MainActivity.class);
    PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(contentIntent);

    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
    notificationManager.notify(SYNC_WORKER_NOTIFICATION_ID, builder.build());

    return builder;
  }
}
