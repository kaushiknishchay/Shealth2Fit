package com.shealth2fit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import static com.shealth2fit.SyncWorker.DATE_END_TIMESTAMP_KEY;
import static com.shealth2fit.SyncWorker.DATE_START_TIMESTAMP_KEY;
import static com.shealth2fit.util.DateUtil.TODAY_START_UTC_TIME;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  private Context mContext;

  public SyncService() {
    super("SyncService");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    mContext = this.getApplicationContext();
  }

  @Override
  public IBinder onBind(Intent intent) {
    // TODO: Return the communication channel to the service.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    Log.i(TAG, "Service Started");
    long mStartTime = intent.getLongExtra(DATE_START_TIMESTAMP_KEY, TODAY_START_UTC_TIME);
    long mEndTime = intent.getLongExtra(DATE_END_TIMESTAMP_KEY, TODAY_START_UTC_TIME);

    Looper.getMainLooper();

    SyncData syncData = new SyncData(mContext, mStartTime, mEndTime);
    syncData.startNoThread();

    Looper.loop();
  }
}
