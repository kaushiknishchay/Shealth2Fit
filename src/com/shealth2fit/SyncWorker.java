package com.shealth2fit;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.collect.Lists;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.shealth2fit.util.GoogleFitUtil;
import com.shealth2fit.util.NotificationUtil;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static com.shealth2fit.util.NotificationUtil.SYNC_WORKER_CHANNEL_ID;

public class SyncWorker extends Worker {

  public static final String SYNC_WORKER_TAG = "auto_sync_worker";
  public static final String DATE_START_TIMESTAMP_KEY = "sHealth_start_timestamp";
  public static final String DATE_END_TIMESTAMP_KEY = "sHealth_end_timestamp";
  private static long TODAY_START_UTC_TIME;

  static {
    TODAY_START_UTC_TIME = getTodayStartUtcTime();
  }

  private final Context mContext;
  private HealthDataStore mStore;
  private String TAG = "SyncWorker";
  private StepCountReader mReporter;
  private long mStartTimeStamp;

  private String mStartDateString;
  private long mEndTimeStamp;
  private String mEndDateString;

  public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    mContext = getApplicationContext();
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

  @NonNull
  @Override
  public Result doWork() {
    NotificationUtil.createNotificationChannel(mContext);

    NotificationUtil.sendNotification(
     mContext,
     "Starting activity sync",
     "",
     SYNC_WORKER_CHANNEL_ID
    );

    mStartTimeStamp = getInputData().getLong(DATE_START_TIMESTAMP_KEY, TODAY_START_UTC_TIME);
    mEndTimeStamp = getInputData().getLong(DATE_END_TIMESTAMP_KEY, TODAY_START_UTC_TIME + StepCountReader.ONE_DAY);

    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(mStartTimeStamp);
    mStartDateString = cal.getTime().toString();
    cal.setTimeInMillis(mEndTimeStamp);
    mEndDateString = cal.getTime().toString();
    TAG = TAG.concat(" : " + mStartDateString + "-" + mEndDateString);

    Log.i(TAG, "Sync Start for " + mStartDateString + "-" + mEndDateString);

    try {
      Thread storeConnectThread = new Thread() {
        @Override
        public void run() {
          Looper.prepare();
          mStore = new HealthDataStore(mContext, mStoreConnectionListener);

          mStore.connectService(10);

          mReporter = new StepCountReader(mStore, mStepCountObserver);

          Looper.loop();
        }
      };

      storeConnectThread.start();

      storeConnectThread.join();

      return Result.success();
    } catch (Exception e) {
      e.printStackTrace();
      return Result.failure();
    }
  }

  private final HealthDataStore.ConnectionListener mStoreConnectionListener = new HealthDataStore.ConnectionListener() {
    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected: Store Connected");
      mReporter.readStepDataForRange(mStartTimeStamp, mEndTimeStamp);
    }

    @Override
    public void onConnectionFailed(HealthConnectionErrorResult healthConnectionErrorResult) {
      Log.i(TAG, "Store Connection Failed");
      if (healthConnectionErrorResult.hasResolution()) {
        Log.i(TAG, "onConnectionFailed: " + healthConnectionErrorResult.getErrorCode());
      }
    }

    @Override
    public void onDisconnected() {
      Log.i(TAG, "onDisconnected: Store Disconnected");
      Toast.makeText(mContext, "Store Disconnected", Toast.LENGTH_SHORT).show();
    }
  };


  private final StepCountReader.StepCountObserver mStepCountObserver = new StepCountReader.StepCountObserver() {
    @Override
    public void onChanged(long startTime, int count) {
      Log.i(TAG, "onChanged: " + count);
    }

    @Override
    public void onBinningDataChanged(List<StepCountReader.StepBinningData> sHealthRecordedSteps) {
      Log.i(TAG, "Got bin data, total points : " + sHealthRecordedSteps.size());
      Log.i(TAG, "Bin Data : " + sHealthRecordedSteps.toString());

      List<List<StepCountReader.StepBinningData>> partitionedSteps = Lists.partition(sHealthRecordedSteps, 900);

      for (List<StepCountReader.StepBinningData> partitionedStep : partitionedSteps) {
        GoogleFitUtil.insertMultiDataPoints(mContext, partitionedStep);
      }


//      Task<DataReadResponse> readStepData = GoogleFitUtil.readHistoricData(mContext, mStartTimeStamp);
//
//      readStepData.addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
//        @Override
//        public void onComplete(@NonNull Task<DataReadResponse> task) {
//          DataReadResponse daysStepData = task.getResult();
//
//          List<StepCountReader.StepBinningData> gFitRecordedSteps = GoogleFitUtil.getBinDataFromResponse(daysStepData);
//          sHealthRecordedSteps.removeAll(gFitRecordedSteps);
//          Log.i(TAG, "Read Data : Successful");
//
//          if (sHealthRecordedSteps.size() > 0 || mStartTimeStamp < TODAY_START_UTC_TIME) {
//            Log.i(TAG, "Activity Count : " + sHealthRecordedSteps.size());
//            GoogleFitUtil.insertMultiDataPoints(mContext, sHealthRecordedSteps);
//
            NotificationUtil.sendNotification(
             mContext,
             "Activity synced : " + mStartDateString,
             "New Activity Count : " + sHealthRecordedSteps.size(),
             SYNC_WORKER_CHANNEL_ID
            );
//          } else {
//            NotificationUtil.sendNotification(
//             mContext,
//             "No new Activity to sync",
//             "",
//             SYNC_WORKER_CHANNEL_ID
//            );
//          }
//
//          mStore.disconnectService();
//        }
//      });
    }
  };
}
