/**
 * Copyright (C) 2014 Samsung Electronics Co., Ltd. All rights reserved.
 * <p>
 * Mobile Communication Division,
 * Digital Media & Communications Business, Samsung Electronics Co., Ltd.
 * <p>
 * This software and its documentation are confidential and proprietary
 * information of Samsung Electronics Co., Ltd.  No part of the software and
 * documents may be copied, reproduced, transmitted, translated, or reduced to
 * any electronic medium or machine-readable form without the prior written
 * consent of Samsung Electronics.
 * <p>
 * Samsung Electronics makes no representations with respect to the contents,
 * and assumes no responsibility for any errors that might appear in the
 * software and documents. This publication and the contents hereof are subject
 * to change without notice.
 */

package com.shealth2fit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionResult;
import com.samsung.android.sdk.healthdata.HealthResultHolder;
import com.shealth2fit.util.NotificationUtil;
import com.shealth2fit.util.SamsungHealthUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.shealth2fit.SyncWorker.DATE_END_TIMESTAMP_KEY;
import static com.shealth2fit.SyncWorker.DATE_START_TIMESTAMP_KEY;
import static com.shealth2fit.SyncWorker.SYNC_WORKER_TAG;

public class MainActivity extends AppCompatActivity {

  public static final String TAG = "StepDiary";
  private static final int REQUEST_OAUTH_REQUEST_CODE = 187;
  private static final int MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 11;

  @BindView(R.id.total_step_count)
  TextView mStepCountTv;
  @BindView(R.id.date_view)
  TextView mDayTv;
  @BindView(R.id.binning_list)
  ListView mBinningListView;
  @BindView(R.id.syncButton)
  Button mSyncButton;
  @BindView(R.id.sync7DaysButton)
  Button mSync7DaysButton;
  @BindView(R.id.sync30DaysButton)
  Button mSync30DaysButton;
  @BindView(R.id.numberOfDays)
  EditText mNumberOfDays;

  private HealthDataStore mStore;
  private StepCountReader mReporter;
  private long mCurrentStartTime;
  private final HealthResultHolder.ResultListener<PermissionResult> mPermissionListener =
   new HealthResultHolder.ResultListener<PermissionResult>() {

     @Override
     public void onResult(PermissionResult result) {
       Map<PermissionKey, Boolean> resultMap = result.getResultMap();
       // Show a permission alarm and clear step count if permissions are not acquired
       if (resultMap.values().contains(Boolean.FALSE)) {
         updateStepCountView("", new Date().getTime());
         showPermissionAlarmDialog();
       } else {
         // Get the daily step count of a particular day and display it
         mReporter.requestDailyStepCount(mCurrentStartTime);
       }
     }
   };
  private Context mContext;
  private Activity mActivity;
  private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {
    @Override
    public void onConnected() {
      Log.d(TAG, "onConnected");
      if (SamsungHealthUtil.isPermissionAcquired(mStore)) {
        mReporter.requestDailyStepCount(mCurrentStartTime);
        mReporter.readSleepData();
      } else {
        SamsungHealthUtil.requestPermission(mStore, mActivity, mPermissionListener);
      }
    }

    @Override
    public void onConnectionFailed(HealthConnectionErrorResult error) {
      Log.d(TAG, "onConnectionFailed");
      showConnectionFailureDialog(error);
    }

    @Override
    public void onDisconnected() {
      Log.d(TAG, "onDisconnected");
      if (!isFinishing()) {
        mStore.connectService();
      }
    }
  };
  private FitnessOptions fitnessOptions;
  private BinningListAdapter mBinningListAdapter;
  private final StepCountReader.StepCountObserver mStepCountObserver = new StepCountReader.StepCountObserver() {
    @Override
    public void onChanged(long startTime, int count) {
      Log.i(TAG, "onChanged: " + count);
      updateStepCountView(String.valueOf(count), startTime);
    }

    @Override
    public void onBinningDataChanged(List<StepCountReader.StepBinningData> stepBinningDataList) {
      updateBinningChartView(stepBinningDataList);
    }
  };
  private WorkManager workManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    mActivity = this;
    mContext = this.getApplicationContext();
    workManager = WorkManager.getInstance(mContext);

    NotificationUtil.createNotificationChannel(mContext);

    // Get the start time of today in local
    mCurrentStartTime = StepCountReader.TODAY_START_UTC_TIME;
    mDayTv.setText(getFormattedTime());

    // Create a HealthDataStore instance and set its listener
    mStore = new HealthDataStore(this, mConnectionListener);

    // Request the connection to the health data store
    mStore.connectService();
    mReporter = new StepCountReader(mStore, mStepCountObserver);

    mBinningListAdapter = new BinningListAdapter();
    mBinningListView.setAdapter(mBinningListAdapter);

    fitnessOptions =
     FitnessOptions.builder()
      .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
      .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
      .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
      .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
      .build();

    if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACTIVITY_RECOGNITION)
     != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ActivityCompat.requestPermissions(
       mActivity,
       new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
       MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION
      );
    } else {
      checkForGooglePermissions();
    }
  }

  private void checkForGooglePermissions() {
    if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(mContext), fitnessOptions)) {
      GoogleSignIn.requestPermissions(
       mActivity,
       REQUEST_OAUTH_REQUEST_CODE,
       GoogleSignIn.getLastSignedInAccount(mContext),
       fitnessOptions
      );
      Log.i(TAG, "Got GFit Permissions");
    } else {
      setupWorker();
    }
  }

  private void setupWorker() {
    WorkManager.getInstance(mContext).cancelAllWorkByTag(SYNC_WORKER_TAG);

    Constraints constraints = new Constraints.Builder()
     .setRequiresCharging(false)
     .build();

    PeriodicWorkRequest saveRequest =
     new PeriodicWorkRequest.Builder(SyncWorker.class, 1, TimeUnit.MINUTES)
      .setConstraints(constraints)
      .addTag(SYNC_WORKER_TAG)
      .build();

    workManager.enqueue(saveRequest);
  }

  @OnClick(R.id.syncButton)
  public void syncOnClick() {
    Log.i(TAG, "Sync for : " + mCurrentStartTime);
    syncDataForDate(mCurrentStartTime, mCurrentStartTime + StepCountReader.ONE_DAY);
  }

  @OnClick(R.id.sync7DaysButton)
  public void sync7DaysOnClick() {
    Log.i(TAG, "Sync for last 7 Days");
    long endTimeInMillis = StepCountReader.TODAY_START_UTC_TIME;
    long startTimeInMillis = endTimeInMillis - (StepCountReader.ONE_DAY * 7);

    syncDataForDate(startTimeInMillis, endTimeInMillis);
  }

  @OnClick(R.id.sync30DaysButton)
  public void sync30DaysOnClick() {
    long endTimeInMillis = StepCountReader.TODAY_START_UTC_TIME;

    int daysCount = Integer.parseInt(mNumberOfDays.getText().toString());
    Log.i(TAG, "Sync for last " + daysCount + " Days");

    if (daysCount > 0) {
      long startTimeInMillis = endTimeInMillis - (StepCountReader.ONE_DAY * daysCount);
      syncDataForDate(startTimeInMillis, endTimeInMillis);
    } else {
      Toast.makeText(mContext, "Invalid Days count given", Toast.LENGTH_LONG).show();
    }
  }

  private void syncDataForDate(long startTimeInMillis, long endTimeInMillis) {
    String workerTag = "Date_" + startTimeInMillis + "_" + endTimeInMillis;

    workManager.cancelAllWorkByTag(workerTag);

    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(startTimeInMillis);
    String dateString = cal.getTime().toString();

    Log.i(TAG, "Sync for " + dateString);
    Data.Builder builder = new Data.Builder();
    builder.putLong(DATE_START_TIMESTAMP_KEY, startTimeInMillis);
    builder.putLong(DATE_END_TIMESTAMP_KEY, endTimeInMillis);

    OneTimeWorkRequest syncWorkRequest =
     new OneTimeWorkRequest.Builder(SyncWorker.class)
      .setInputData(builder.build())
      .addTag(workerTag)
//      .setInitialDelay(1, TimeUnit.SECONDS)
      .build();

    workManager.enqueue(syncWorkRequest);

    workManager.getWorkInfoByIdLiveData(syncWorkRequest.getId())
     .observe(this, new Observer<WorkInfo>() {
       @Override
       public void onChanged(@Nullable WorkInfo workInfo) {
         if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
           Log.i(TAG, "Work finished!");
         }
       }
     });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
         && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.i(TAG, "Got Manifest Permissions");

          // permission was granted, yay! Do the
          // contacts-related task you need to do.
        } else {
          // permission denied, boo! Disable the
          // functionality that depends on this permission.
        }
        checkForGooglePermissions();
        return;
      }
      case REQUEST_OAUTH_REQUEST_CODE: {
        setupWorker();
        return;
      }

      // other 'case' lines to check for other
      // permissions this app might request.
    }
  }

  @Override
  public void onDestroy() {
    mStore.disconnectService();
    super.onDestroy();
  }

  @Override
  public void onResume() {
    super.onResume();
    mReporter.requestDailyStepCount(mCurrentStartTime);
  }

  @OnClick(R.id.move_before)
  void onClickBeforeButton() {
    mCurrentStartTime -= StepCountReader.ONE_DAY;
    mDayTv.setText(getFormattedTime());
    mBinningListAdapter.changeDataSet(Collections.<StepCountReader.StepBinningData>emptyList());
    mReporter.requestDailyStepCount(mCurrentStartTime);
    syncDataForDate(mCurrentStartTime, mCurrentStartTime + StepCountReader.ONE_DAY);
  }

  @OnClick(R.id.move_next)
  void onClickNextButton() {
    mCurrentStartTime += StepCountReader.ONE_DAY;
    mDayTv.setText(getFormattedTime());
    mBinningListAdapter.changeDataSet(Collections.emptyList());
    mReporter.requestDailyStepCount(mCurrentStartTime);
  }

  private String getFormattedTime() {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd (E)", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(mCurrentStartTime);
  }

  private void updateStepCountView(final String count, final long startTime) {
    // Display the today step count so far
    runOnUiThread(() -> {
      mStepCountTv.setText(count);
//            insertData(startTime, Integer.parseInt(count));
    });
  }

    /*
      Functions related to Store permission setup
     */

  private void updateBinningChartView(List<StepCountReader.StepBinningData> stepBinningDataList) {
    // the following code will be replaced with chart drawing code
    Log.i(TAG, "updateBinningChartView: " + stepBinningDataList.size());
    mBinningListAdapter.changeDataSet(stepBinningDataList);
//        for (StepCountReader.StepBinningData data : stepBinningDataList) {
//            Log.i(TAG, "TIME : " + data.time + "  COUNT : " + data.count);
//        }
//        insertMultiDataPoints(stepBinningDataList);
  }

  private void showPermissionAlarmDialog() {
    if (isFinishing()) {
      return;
    }

    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
    alert.setTitle(R.string.notice)
     .setMessage(R.string.msg_perm_acquired)
     .setPositiveButton(R.string.ok, null)
     .show();
  }

  private void showConnectionFailureDialog(final HealthConnectionErrorResult error) {
    if (isFinishing()) {
      return;
    }

    AlertDialog.Builder alert = new AlertDialog.Builder(this);

    if (error.hasResolution()) {
      switch (error.getErrorCode()) {
        case HealthConnectionErrorResult.PLATFORM_NOT_INSTALLED:
          alert.setMessage(R.string.msg_req_install);
          break;
        case HealthConnectionErrorResult.OLD_VERSION_PLATFORM:
          alert.setMessage(R.string.msg_req_upgrade);
          break;
        case HealthConnectionErrorResult.PLATFORM_DISABLED:
          alert.setMessage(R.string.msg_req_enable);
          break;
        case HealthConnectionErrorResult.USER_AGREEMENT_NEEDED:
          alert.setMessage(R.string.msg_req_agree);
          break;
        default:
          alert.setMessage(R.string.msg_req_available);
          break;
      }
    } else {
      alert.setMessage(R.string.msg_conn_not_available);
    }

    alert.setPositiveButton(R.string.ok, (dialog, id) -> {
      if (error.hasResolution()) {
        error.resolve(MainActivity.this);
      }
    });

    if (error.hasResolution()) {
      alert.setNegativeButton(R.string.cancel, null);
    }

    alert.show();
  }

  /**
   * Creates a {@link DataSet} and inserts it into user's Google Fit history.
   */
  private Task<Void> insertData(final long startTime, final Integer stepCount) {
    // Create a new dataset and insertion request.
    DataSet dataSet = insertFitnessData(startTime, stepCount);

    // Then, invoke the History API to insert the data.
    Log.i(TAG, "Inserting the dataset in the History API.");

    Log.i(TAG, "insertData: mContext " + mContext);
    Log.i(TAG, "insertData: mActivity " + mActivity);
    if (mContext != null) {
      return Fitness.getHistoryClient(mActivity, GoogleSignIn.getAccountForExtension(mContext, fitnessOptions))
       .insertData(dataSet)
       .addOnCompleteListener(
        new OnCompleteListener<Void>() {
          @Override
          public void onComplete(@NonNull Task<Void> task) {
            if (task.isSuccessful()) {
              // At this point, the data has been inserted and can be read.
              Log.i(TAG, "Data insert was successful!");
            } else {
              Log.e(TAG, "There was a problem inserting the dataset.", task.getException());
            }
          }
        });
    }
    return null;
  }

  /**
   * Creates and returns a {@link DataSet} of step count data for insertion using the History API.
   */
  private DataSet insertFitnessData(final long startTime, final Integer stepCount) {
    Log.i(TAG, "Creating a new data insert request.");

    // [START build_insert_data_request]
    // Set a start and end time for our data, using a start time of 1 hour before this moment.
    Calendar cal = Calendar.getInstance();
    Date startDate = new Date(startTime);
    cal.setTime(startDate);
    long startSec = cal.getTimeInMillis();

    cal.add(Calendar.MINUTE, +10);
    long endTime = cal.getTimeInMillis();
    Log.i(TAG, "startDate " + startDate.toString() + "--" + startTime);
    Log.i(TAG, "endTime " + endTime);

    // Create a data source
    DataSource dataSource =
     new DataSource.Builder()
      .setAppPackageName(mContext)
      .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
      .setStreamName(TAG + " - step count")
      .setType(DataSource.TYPE_RAW)
      .build();

    // Create a data set
    int stepCountDelta = stepCount;
    DataSet dataSet = DataSet.create(dataSource);

    if (stepCount > 0) {
      // For each data point, specify a start time, end time, and the data value -- in this case,
      // the number of new steps.
      DataPoint dataPoint =
       dataSet.createDataPoint().setTimeInterval(startSec, endTime, TimeUnit.MILLISECONDS);
      dataPoint.getValue(Field.FIELD_STEPS).setInt(stepCountDelta);
      try {
        dataSet.add(dataPoint);
      } catch (Exception e) {
        Log.i(TAG, "dataPoint Fail: " + dataPoint.toString());
        Log.i(TAG, "Failed to add Data: " + e.getStackTrace().toString());
      }
      // [END build_insert_data_request]

      return dataSet;
    }
    return null;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(android.view.MenuItem item) {

    if (item.getItemId() == R.id.connect) {
      SamsungHealthUtil.requestPermission(mStore, mActivity, mPermissionListener);
    }

    return true;
  }

  @Override
  public void onBackPressed() {
    moveTaskToBack(true);
  }

  private class BinningListAdapter extends BaseAdapter {

    private List<StepCountReader.StepBinningData> mDataList = new ArrayList<>();

    void changeDataSet(List<StepCountReader.StepBinningData> dataList) {
      mDataList = dataList;
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return mDataList.size();
    }

    @Override
    public Object getItem(int position) {
      return mDataList.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

      if (convertView == null) {
        convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
      }

      ((TextView) convertView.findViewById(android.R.id.text1)).setText(mDataList.get(position).count + " steps");
      ((TextView) convertView.findViewById(android.R.id.text2)).setText(new Date(mDataList.get(position).time).toString());
      return convertView;
    }
  }
}
