package com.shealth2fit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.shealth2fit.util.GoogleFitUtil;
import com.shealth2fit.util.SamsungHealthUtil;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class SyncService extends Service {
  private static final String TAG = "SyncWorker";
  private static final String CHANNEL_ID = "Sync Service";
  private static final int NOTIFICATION_ID = 12345;
  private static long TODAY_START_UTC_TIME;

  static {
    TODAY_START_UTC_TIME = getTodayStartUtcTime();
  }

  private Context mContext;
  private HealthDataStore mStore;
  private boolean isStoreConnected;
  private StepCountReader mReporter;
  private long mTodaysUTCTime;
  private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {
    @Override
    public void onConnected() {
      Log.d(TAG, "onConnected");
      if (SamsungHealthUtil.isPermissionAcquired(mStore)) {
        Log.d(TAG, "onConnected: Permission Acquired");
        isStoreConnected = true;

        // Fetch the data for today's date
        mReporter.requestDailyStepCount(mTodaysUTCTime);
      } else {
        isStoreConnected = false;
      }
    }

    @Override
    public void onConnectionFailed(HealthConnectionErrorResult healthConnectionErrorResult) {
      isStoreConnected = false;
    }

    @Override
    public void onDisconnected() {
      isStoreConnected = false;
    }
  };
  private Task<DataReadResponse> readStepData;
  private NotificationCompat.Builder sentNotification;
  private String dateForSyncing;
  private final StepCountReader.StepCountObserver mStepCountObserver = new StepCountReader.StepCountObserver() {
    @Override
    public void onChanged(long startTime, int count) {
      Log.i(TAG, "onChanged: " + count);
    }

    @Override
    public void onBinningDataChanged(List<StepCountReader.StepBinningData> sHealthRecordedSteps) {
      Log.i(TAG, "Got bin data, total points : " + sHealthRecordedSteps.size());

      readStepData = GoogleFitUtil.readHistoricData(mContext, mTodaysUTCTime);

      readStepData.addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
        @Override
        public void onComplete(@NonNull Task<DataReadResponse> task) {
          DataReadResponse daysStepData = task.getResult();

          List<StepCountReader.StepBinningData> gFitRecordedSteps = GoogleFitUtil.getBinDataFromResponse(daysStepData);
          sHealthRecordedSteps.removeAll(gFitRecordedSteps);
          Log.i(TAG, "Read Data : sHealthRecordedSteps " + sHealthRecordedSteps.toString());
          Log.i(TAG, "Read Data : Successful");

          NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);

          if (sHealthRecordedSteps.size() > 0 || Long.parseLong(dateForSyncing) < TODAY_START_UTC_TIME) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(Long.parseLong(dateForSyncing));
            Date date = cal.getTime();

            sentNotification.setContentText("New Activity Count : " + sHealthRecordedSteps.size());
            notificationManager.notify(NOTIFICATION_ID, sentNotification.build());

            GoogleFitUtil.insertMultiDataPoints(mContext, sHealthRecordedSteps);

            sentNotification.setContentTitle("Synced : " + date.toString());
            notificationManager.notify(NOTIFICATION_ID, sentNotification.build());
          } else {
            sentNotification.setContentText("No New Activity to sync");
            notificationManager.notify(NOTIFICATION_ID, sentNotification.build());
          }
        }
      });
    }
  };


  public SyncService() {
  }

  private static long getTodayStartUtcTime() {
    Calendar today = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    Log.d(MainActivity.TAG, "Today : " + today.getTimeInMillis());

    today.set(Calendar.HOUR_OF_DAY, 0);
    today.set(Calendar.MINUTE, 0);
    today.set(Calendar.SECOND, 0);
    today.set(Calendar.MILLISECOND, 0);

    return today.getTimeInMillis();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    mContext = this.getApplicationContext();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "Service Started");
    Bundle extras = intent.getExtras();
    dateForSyncing = extras.get("day_time") + "";
    Log.i(TAG, "Service started for : " + dateForSyncing);
    if (dateForSyncing == null) {
      dateForSyncing = TODAY_START_UTC_TIME + "";
    }
    doWork();
    return Service.START_NOT_STICKY;
  }

  public void doWork() {
    createNotificationChannel();
    sentNotification = sendNotification(0);

    // Set the today's date for syncing

    mTodaysUTCTime = TODAY_START_UTC_TIME;
    long dateForSyncingInMillis = Long.parseLong(dateForSyncing);
    Calendar cal = Calendar.getInstance();
    if (dateForSyncingInMillis > 0) {
      cal.setTimeInMillis(dateForSyncingInMillis);
      mTodaysUTCTime = dateForSyncingInMillis;
    } else {
      mTodaysUTCTime = TODAY_START_UTC_TIME;
      cal.setTimeInMillis(mTodaysUTCTime);
    }

    Log.i(TAG, "Start Syncing for : " + cal.getTime().toString());

    // Create a HealthDataStore instance and set its listener
    mStore = new HealthDataStore(mContext, mConnectionListener);

    // Request the connection to the health data store
    Log.d(TAG, "connectService()");
    mStore.connectService();
    mReporter = new StepCountReader(mStore, mStepCountObserver);
  }

  private void createNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      CharSequence name = getString(R.string.channel_name);
      String description = getString(R.string.channel_description);
      int importance = NotificationManager.IMPORTANCE_DEFAULT;
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
      channel.setDescription(description);
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      NotificationManager notificationManager = getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  /* Helpers */

  private NotificationCompat.Builder sendNotification(int count) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(Long.parseLong(dateForSyncing));
    Date date = cal.getTime();
    NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
     .setSmallIcon(R.drawable.ic_launcher)
     .setContentTitle("Syncing : " + date.toString())
     .setPriority(NotificationCompat.PRIORITY_DEFAULT);

    if (count > 0) {
      builder.setContentText("New Activity Count : " + count);
    } else {
      builder.setContentText("Fetching data from Samsung Health to sync");
    }


    Intent targetIntent = new Intent(mContext, MainActivity.class);
    PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(contentIntent);

    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
    notificationManager.notify(NOTIFICATION_ID, builder.build());

    return builder;
  }

  @Override
  public IBinder onBind(Intent intent) {
    // TODO: Return the communication channel to the service.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  private class CusTask extends AsyncTask {
    @Override
    protected void onPostExecute(Object o) {
      List<StepCountReader.StepBinningData> response = GoogleFitUtil.getBinDataFromResponse((DataReadResponse) o);
    }

    @Override
    protected DataReadResponse doInBackground(Object[] objects) {
      try {
        return Tasks.await(readStepData);
      } catch (ExecutionException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return null;
    }
  }
}
