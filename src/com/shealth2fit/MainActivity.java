package com.shealth2fit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.archit.calendardaterangepicker.customviews.DateRangeCalendarView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionResult;
import com.samsung.android.sdk.healthdata.HealthResultHolder;
import com.shealth2fit.util.DateUtil;
import com.shealth2fit.util.NotificationUtil;
import com.shealth2fit.util.SamsungHealthUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.shealth2fit.SyncWorker.DATE_END_TIMESTAMP_KEY;
import static com.shealth2fit.SyncWorker.DATE_START_TIMESTAMP_KEY;
import static com.shealth2fit.SyncWorker.SYNC_WORKER_TAG;
import static com.shealth2fit.util.SamsungHealthUtil.calorieToString;

public class MainActivity extends AppCompatActivity {

  public static final String TAG = "SHealth2Fit";
  private static final int REQUEST_OAUTH_REQUEST_CODE = 187;
  private static final int MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 11;

  @BindView(R.id.total_step_count)
  TextView mStepCountTv;
  @BindView(R.id.total_calories_count)
  TextView mCaloriesCountTv;
  @BindView(R.id.binning_list)
  ListView mBinningListView;
  @BindView(R.id.singleMultiModeSwitch)
  Switch mModeSwitch;
  @BindView(R.id.calendarPicker)
  DateRangeCalendarView mCalendar;

  private HealthDataStore mStore;
  private StepCountReader mReporter;
  private long mTodayTimeInUTC;
  private final HealthResultHolder.ResultListener<PermissionResult> mPermissionListener =
   new HealthResultHolder.ResultListener<PermissionResult>() {

     @Override
     public void onResult(PermissionResult result) {
       Map<PermissionKey, Boolean> resultMap = result.getResultMap();
       // Show a permission alarm and clear step count if permissions are not acquired
       if (resultMap.values().contains(Boolean.FALSE)) {
         updateStepCountView("", 0);
         showPermissionAlarmDialog();
       } else {
         // Get the daily step count of a particular day and display it
         mReporter.requestDailyStepCount(mTodayTimeInUTC);
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
        mReporter.requestDailyStepCount(mTodayTimeInUTC);
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
    public void onChanged(long startTime, int count, float totalCalories) {
      updateStepCountView(String.valueOf(count), totalCalories);
    }

    @Override
    public void onBinningDataChanged(int totalStepCount, float totalCalories, List<StepCountReader.StepBinningData> stepBinningDataList) {
      updateStepCountView(String.valueOf(totalStepCount), totalCalories);
      updateBinningChartView(stepBinningDataList);
    }

    @Override
    public void onBinningDataChanged(List<StepCountReader.StepBinningData> stepBinningDataList) {
      updateBinningChartView(stepBinningDataList);
    }
  };
  private WorkManager workManager;
  private boolean isRangeModeSelected = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    mTodayTimeInUTC = DateUtil.toUTC(Calendar.getInstance()).getTimeInMillis();
    mActivity = this;
    mContext = this.getApplicationContext();
    workManager = WorkManager.getInstance(mContext);

    NotificationUtil.createNotificationChannel(mContext);

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

    Calendar startSelectionDate = Calendar.getInstance();
    startSelectionDate.setTimeInMillis(startSelectionDate.getTimeInMillis());

    Calendar endSelectionDate = (Calendar) startSelectionDate.clone();
    endSelectionDate.add(Calendar.DATE, 0);

    mCalendar.setSelectedDateRange(startSelectionDate, endSelectionDate);

    mCalendar.setCalendarListener(new DateRangeCalendarView.CalendarListener() {
      @Override
      public void onFirstDateSelected(@NonNull Calendar startDate) {
        if (!isRangeModeSelected) {
          updateStepCountView("0", 0);

          mCalendar.setSelectedDateRange(startDate, startDate);
          mCalendar.setSelected(true);

          Calendar calendar = DateUtil.toUTC(startDate);
          long mStartDateUTC = calendar.getTimeInMillis();

          long mEndDateUTC = Math.min(mStartDateUTC + StepCountReader.ONE_DAY, mTodayTimeInUTC);

          syncDataForDate(mStartDateUTC, mEndDateUTC);

          mBinningListAdapter.changeDataSet(Collections.emptyList());
          mReporter.requestDailyStepCount(mStartDateUTC);
        }
      }

      @Override
      public void onDateRangeSelected(@NonNull Calendar startDate, @NonNull Calendar endDate) {
        if (isRangeModeSelected) {
          Calendar startUTCDate = DateUtil.toUTC(startDate);
          Calendar endUTCDate = DateUtil.toUTC(endDate);
          syncDataForDate(startUTCDate.getTimeInMillis(), Math.min(endUTCDate.getTimeInMillis(), mTodayTimeInUTC));
        }
      }
    });

    mModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
      isRangeModeSelected = isChecked;
      mCalendar.resetAllSelectedViews();
    });
  }

  private void checkForGooglePermissions() {
    if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(mContext), fitnessOptions)) {
      GoogleSignIn.requestPermissions(
       mActivity,
       REQUEST_OAUTH_REQUEST_CODE,
       GoogleSignIn.getLastSignedInAccount(mContext),
       fitnessOptions
      );
      Log.i(TAG, "Got Google Fit Permissions");
    } else {
      mReporter.requestDailyStepCount(mTodayTimeInUTC);
      setupWorker();
    }
  }

  private void setupWorker() {
    WorkManager.getInstance(mContext).cancelAllWorkByTag(SYNC_WORKER_TAG);

    Constraints constraints = new Constraints.Builder()
     .setRequiresCharging(false)
     .build();

    PeriodicWorkRequest saveRequest =
     new PeriodicWorkRequest.Builder(SyncWorker.class, 6, TimeUnit.HOURS)
      .setConstraints(constraints)
      .addTag(SYNC_WORKER_TAG)
      .build();

    workManager.enqueue(saveRequest);
  }

  private void syncDataForDate(long startTimeInMillis, long endTimeInMillis) {
    Intent syncServiceIntent = new Intent(this, SyncService.class);
    syncServiceIntent.putExtra(DATE_START_TIMESTAMP_KEY, startTimeInMillis);
    syncServiceIntent.putExtra(DATE_END_TIMESTAMP_KEY, endTimeInMillis);
    startService(syncServiceIntent);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
         && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          checkForGooglePermissions();
        }
        return;
      }
      case REQUEST_OAUTH_REQUEST_CODE: {
        setupWorker();
        return;
      }
      default:
        throw new IllegalStateException("Unexpected value: " + requestCode);
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
    mReporter.requestDailyStepCount(mTodayTimeInUTC);
  }

  private void updateStepCountView(final String count, final float totalCalories) {
    // Display the today step count so far
    runOnUiThread(() -> {
      mStepCountTv.setText(count);
      mCaloriesCountTv.setText(calorieToString(totalCalories));
//            insertData(startTime, Integer.parseInt(count));
    });
  }

  private void updateBinningChartView(List<StepCountReader.StepBinningData> stepBinningDataList) {
    // the following code will be replaced with chart drawing code
    Log.i(TAG, "updateBinningChartView: " + stepBinningDataList.size());
    mBinningListAdapter.changeDataSet(stepBinningDataList);
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

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

      if (convertView == null) {
        convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
      }

      ((TextView) convertView.findViewById(android.R.id.text1))
       .setText(mDataList.get(position).getCalorie() + " kcal " + mDataList.get(position).count + " steps");

      ((TextView) convertView.findViewById(android.R.id.text2))
       .setText(new Date(mDataList.get(position).time).toString());

      return convertView;
    }
  }
}
