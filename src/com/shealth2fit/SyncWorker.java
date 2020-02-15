package com.shealth2fit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.shealth2fit.util.NotificationUtil;

import static com.shealth2fit.util.DateUtil.TODAY_START_UTC_TIME;

public class SyncWorker extends Worker {

  static final String SYNC_WORKER_TAG = "auto_sync_worker";
  static final String DATE_START_TIMESTAMP_KEY = "sHealth_start_timestamp";
  static final String DATE_END_TIMESTAMP_KEY = "sHealth_end_timestamp";

  private final Context mContext;
  private String TAG = "SyncWorker";

  public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    mContext = getApplicationContext();
  }

  @NonNull
  @Override
  public Result doWork() {
    NotificationUtil.createNotificationChannel(mContext);

    long mStartTimeStamp = getInputData().getLong(DATE_START_TIMESTAMP_KEY, TODAY_START_UTC_TIME);
    long mEndTimeStamp = getInputData().getLong(DATE_END_TIMESTAMP_KEY, TODAY_START_UTC_TIME + StepCountReader.ONE_DAY);

    try {
      SyncData syncData = new SyncData(mContext, mStartTimeStamp, mEndTimeStamp);
      syncData.start();

      return Result.success();
    } catch (Exception e) {
      return Result.failure();
    }
  }
}
