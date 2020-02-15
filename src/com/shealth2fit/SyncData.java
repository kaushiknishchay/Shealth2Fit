package com.shealth2fit;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.google.common.collect.Lists;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.shealth2fit.util.GoogleFitUtil;
import com.shealth2fit.util.NotificationUtil;

import java.util.List;

import static com.shealth2fit.util.DateUtil.getDateStringFromMillis;
import static com.shealth2fit.util.NotificationUtil.SYNC_WORKER_CHANNEL_ID;

class SyncData {

  private final static String TAG = "SyncData";
  private final Context mContext;
  private final long mStartTimeStamp;
  private final long mEndTimeStamp;
  private String mEndDateString;
  private String mStartDateString;
  private HealthDataStore mStore;
  private StepCountReader mReporter;

  SyncData(Context context, long mStartTime, long mEndTime) {
    mContext = context;
    mStartTimeStamp = mStartTime;
    mEndTimeStamp = mEndTime;
    mStartDateString = getDateStringFromMillis(mStartTime);
    mEndDateString = getDateStringFromMillis(mEndTime);
  }

  void start() {
    NotificationUtil.createNotificationChannel(mContext);

    NotificationUtil.sendNotification(
     mContext,
     "Starting activity sync",
     mStartDateString + "-" + mEndDateString,
     SYNC_WORKER_CHANNEL_ID,
     true
    );

    Log.i(TAG, "Sync Start for " + mStartDateString + "-" + mEndDateString);

    try {
      Thread storeConnectThread = new Thread() {
        @Override
        public void run() {
          Looper.prepare();
          mStore = new HealthDataStore(mContext, mStoreConnectionListener);

          mStore.connectService();

          mReporter = new StepCountReader(mStore, mStepCountObserver);

          Looper.loop();
        }
      };

      storeConnectThread.start();

      storeConnectThread.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  void startNoThread() {
    NotificationUtil.createNotificationChannel(mContext);

    NotificationUtil.sendNotification(
     mContext,
     "Starting activity sync",
     mStartDateString + "-" + mEndDateString,
     SYNC_WORKER_CHANNEL_ID,
     false
    );

    Log.i(TAG, "Sync Start for " + mStartDateString + "-" + mEndDateString);

    try {
      mStore = new HealthDataStore(mContext, mStoreConnectionListener);

      mStore.connectService();

      mReporter = new StepCountReader(mStore, mStepCountObserver);
    } catch (Exception e) {
      e.printStackTrace();
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
      NotificationUtil.sendNotification(
       mContext,
       "Samsung Health Connection Failed",
       mStartDateString + "-" + mEndDateString,
       SYNC_WORKER_CHANNEL_ID
      );
    }

    @Override
    public void onDisconnected() {
      Log.i(TAG, "onDisconnected: Store Disconnected");
    }
  };


  private final StepCountReader.StepCountObserver mStepCountObserver = new StepCountReader.StepCountObserver() {
    @Override
    public void onChanged(long startTime, int count) {
      Log.i(TAG, "onChanged: " + count);
    }

    @Override
    public void onBinningDataChanged(int totalStepCount, List<StepCountReader.StepBinningData> sHealthRecordedSteps) {
      NotificationUtil.sendNotification(
       mContext,
       "Fetched Data from Samsung Health",
       mStartDateString + "-" + mEndDateString,
       SYNC_WORKER_CHANNEL_ID,
       true
      );

      List<List<StepCountReader.StepBinningData>> partitionedSteps = Lists.partition(sHealthRecordedSteps, 900);

      for (List<StepCountReader.StepBinningData> partitionedStep : partitionedSteps) {
        GoogleFitUtil.insertMultiDataPoints(mContext, partitionedStep);
      }

      NotificationUtil.sendNotification(
       mContext,
       "Synced: " + mStartDateString + "-" + mEndDateString,
       "Activity Count: " + sHealthRecordedSteps.size() + " (" + totalStepCount + " steps)",
       SYNC_WORKER_CHANNEL_ID,
       false
      );
    }

    @Override
    public void onBinningDataChanged(List<StepCountReader.StepBinningData> sHealthRecordedSteps) {
      Log.i(TAG, "Got bin data, total points : " + sHealthRecordedSteps.size());
      Log.i(TAG, "Bin Data : " + sHealthRecordedSteps.toString());
    }
  };
}
