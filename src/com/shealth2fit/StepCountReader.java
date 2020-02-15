package com.shealth2fit;

import android.util.Log;

import androidx.annotation.Nullable;

import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.AggregateFunction;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.TimeGroupUnit;
import com.samsung.android.sdk.healthdata.HealthDataResolver.Filter;
import com.samsung.android.sdk.healthdata.HealthDataResolver.ReadRequest;
import com.samsung.android.sdk.healthdata.HealthDataResolver.SortOrder;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthDataUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.shealth2fit.util.DateUtil.TODAY_START_UTC_TIME;


public class StepCountReader {

  public static final String STEP_SUMMARY_DATA_TYPE_NAME = "com.samsung.shealth.step_daily_trend";
  static final long ONE_DAY = 24 * 60 * 60 * 1000;
  private static final String TAG = "StepCountReader";
  private static final String PROPERTY_TIME = "day_time";
  private static final String PROPERTY_COUNT = "count";
  private static final String PROPERTY_BINNING_DATA = "binning_data";
  private static final String ALIAS_TOTAL_COUNT = "count";
  private static final String ALIAS_DEVICE_UUID = "deviceuuid";
  private static final String ALIAS_BINNING_TIME = "binning_time";

  private final HealthDataResolver mResolver;
  private final StepCountObserver mObserver;

  StepCountReader(HealthDataStore store, StepCountObserver observer) {
    mResolver = new HealthDataResolver(store, null);
    mObserver = observer;
  }

  private static List<StepBinningData> getBinningData(byte[] zip, long startTime) {
    String pattern = "yyyy-MM-dd";
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.getDefault());

    String date = simpleDateFormat.format(new Date(startTime));
    SimpleDateFormat dFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    List<StepBinningData> binningDataList = HealthDataUtil.getStructuredDataList(zip, StepBinningData.class);
    for (int i = binningDataList.size() - 1; i >= 0; i--) {
      StepBinningData binItem = binningDataList.get(i);
      if (binItem.count == 0) {
        binningDataList.remove(i);
      } else {
        Date binDate = new Date();

        try {
          binDate = dFormat.parse(date + " " + String.format(Locale.US, "%02d:%02d", i / 6, (i % 6) * 10));
        } catch (ParseException e) {
          e.printStackTrace();
        }

        binningDataList.get(i).time = Objects.requireNonNull(binDate).getTime();
      }
    }

    return binningDataList;
  }

  void readSleepData() {
    ReadRequest request = new ReadRequest.Builder()
     .setDataType(HealthConstants.Sleep.HEALTH_DATA_TYPE)
     .setTimeAfter(TODAY_START_UTC_TIME)

//     .setFilter(
////      Filter.and(Filter.eq(HealthConstants.Sleep.START_TIME, TODAY_START_UTC_TIME),
//       // filtering source type "combined(-2)"
////       Filter.eq("source_type", -2)
////     )
//     )
     .build();

    mResolver.read(request).setResultListener(responseData -> {
      Iterator<HealthData> iterator = responseData.iterator();
      if (iterator.hasNext()) {
        HealthData data = iterator.next();
        long startTime = data.getLong(HealthConstants.Sleep.START_TIME);
        long endTime = data.getLong(HealthConstants.Sleep.END_TIME);
        long timeOffset = data.getLong(HealthConstants.Sleep.TIME_OFFSET);

//        startTime += timeOffset;
//        endTime += timeOffset;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        Log.i(TAG, "Start: " + cal.getTime().toString());
        cal.setTimeInMillis(endTime);
        Log.i(TAG, "End: " + cal.getTime().toString());
        Log.i(TAG, "readSleepData: " + data.toString());
        Log.i(TAG, "readSleepData: " + data.getContentValues());
      }
    });
  }

  // Get the daily total step count of a specified day
  void requestDailyStepCount(long startTime) {
    if (startTime >= TODAY_START_UTC_TIME) {
      // Get today step count
      readStepCount(startTime);
    } else {
      // Get historical step count
      readStepDailyTrend(startTime);
    }
  }

  private void readStepCount(final long startTime) {
    Log.i(TAG, "Read Step Count for Today : " + startTime);

    // Get sum of step counts by device
    AggregateRequest request = new AggregateRequest.Builder()
     .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
     .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.COUNT, ALIAS_TOTAL_COUNT)
     .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.CALORIE, HealthConstants.StepCount.CALORIE)
     .addGroup(HealthConstants.StepCount.DEVICE_UUID, ALIAS_DEVICE_UUID)
     .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET,
      startTime, startTime + ONE_DAY)
     .setSort(ALIAS_TOTAL_COUNT, SortOrder.DESC)
     .build();

    try {
      mResolver.aggregate(request).setResultListener(result -> {
        int totalCount = 0;
        float totalCalories = 0;
        String deviceUuid = null;

        try {
          Iterator<HealthData> iterator = result.iterator();
          if (iterator.hasNext()) {
            HealthData data = iterator.next();
            totalCount = data.getInt(ALIAS_TOTAL_COUNT);
            totalCalories += data.getFloat(HealthConstants.StepCount.CALORIE);
            deviceUuid = data.getString(ALIAS_DEVICE_UUID);
          }
        } finally {
          result.close();
        }

        if (mObserver != null) {
          mObserver.onChanged(startTime, totalCount, totalCalories);
        }

        if (deviceUuid != null) {
          readStepCountBinning(startTime, deviceUuid);
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Getting step count fails.", e);
    }
  }

  private void readStepDailyTrend(final long startTime) {
    Log.i(TAG, " Read Step Count Trend : " + startTime);

    Filter filter = Filter.and(Filter.eq(PROPERTY_TIME, startTime),
     // filtering source type "combined(-2)"
     Filter.eq("source_type", -2));

    ReadRequest request = new ReadRequest.Builder()
     .setDataType(STEP_SUMMARY_DATA_TYPE_NAME)
     .setProperties(new String[]{PROPERTY_COUNT, PROPERTY_BINNING_DATA, HealthConstants.StepCount.CALORIE})
     .setFilter(filter)
     .build();

    try {
      mResolver.read(request).setResultListener(result -> {
        int totalCount = 0;
        float totalCalories = 0;
        List<StepBinningData> binningDataList = Collections.emptyList();

        try {
          Iterator<HealthData> iterator = result.iterator();
          if (iterator.hasNext()) {
            HealthData data = iterator.next();
            totalCount = data.getInt(PROPERTY_COUNT);
            totalCalories = data.getFloat(HealthConstants.StepCount.CALORIE);
            byte[] binningData = data.getBlob(PROPERTY_BINNING_DATA);
            binningDataList = getBinningData(binningData, startTime);
          }
        } finally {
          result.close();
        }

        if (mObserver != null) {
          mObserver.onChanged(startTime, totalCount, totalCalories);
          mObserver.onBinningDataChanged(totalCount, totalCalories, binningDataList);
        }

      });
    } catch (Exception e) {
      Log.e(TAG, "Getting daily step trend fails.", e);
    }
  }

  public void readStepDataForRange(final long startTime, final long endTime) {
    Log.i(TAG, " Read Step Range : " + startTime + "::" + endTime);

    String[] stepProperties = new String[]{
     PROPERTY_COUNT,
     PROPERTY_BINNING_DATA,
     HealthConstants.StepCount.CALORIE,
     HealthConstants.StepCount.DISTANCE
    };

    List<StepBinningData> binningDataList = new ArrayList<>();

    long startTimeLoop = startTime;
    int totalCount = 0;
    float totalCalories = 0.0f;

    while (startTimeLoop < endTime) {

      Filter filter = Filter.and(
       Filter.eq(PROPERTY_TIME, startTimeLoop),
       Filter.eq("source_type", -2)  // filtering source type "combined(-2)"
      );

      ReadRequest request = new ReadRequest.Builder()
       .setDataType(STEP_SUMMARY_DATA_TYPE_NAME)
       .setProperties(stepProperties)
       .setFilter(filter)
       .build();

      HealthDataResolver.ReadResult responseData = mResolver.read(request).await();
      try {
        Iterator<HealthData> iterator = responseData.iterator();
        if (iterator.hasNext()) {
          HealthData data = iterator.next();
          totalCount += data.getInt(PROPERTY_COUNT);
          totalCalories += data.getFloat(HealthConstants.StepCount.CALORIE);
          byte[] binningData = data.getBlob(PROPERTY_BINNING_DATA);

          binningDataList.addAll(getBinningData(binningData, startTimeLoop));
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      responseData.close();
      startTimeLoop += StepCountReader.ONE_DAY;
    }

    Collections.sort(binningDataList, (o1, o2) -> {
      Long time1 = o1.getTime();
      Long time2 = o2.getTime();
      return time1.compareTo(time2);
    });

    Log.i(TAG, "readStepDataForRange: " + binningDataList.size());
    Log.i(TAG, "totalCount: " + totalCount);

    mObserver.onChanged(startTime, totalCount, totalCalories);
    mObserver.onBinningDataChanged(totalCount, totalCalories, binningDataList);
  }

  private void readStepCountBinning(final long startTime, String deviceUuid) {
    Log.i(TAG, "Read Step Count Bin for Today : " + startTime);

    Filter filter = Filter.eq(HealthConstants.StepCount.DEVICE_UUID, deviceUuid);

    // Get 10 minute binning data of a particular device
    AggregateRequest request = new AggregateRequest.Builder()
     .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
     .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.COUNT, ALIAS_TOTAL_COUNT)
     .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.CALORIE, HealthConstants.StepCount.CALORIE)
     .setTimeGroup(TimeGroupUnit.MINUTELY, 10, HealthConstants.StepCount.START_TIME,
      HealthConstants.StepCount.TIME_OFFSET, ALIAS_BINNING_TIME)
     .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET,
      startTime, startTime + ONE_DAY)
     .setFilter(filter)
     .setSort(ALIAS_BINNING_TIME, SortOrder.ASC)
     .build();

    try {
      mResolver.aggregate(request).setResultListener(result -> {

        List<StepBinningData> binningCountArray = new ArrayList<>();

        try {
          for (HealthData data : result) {
            String binningTime = data.getString(ALIAS_BINNING_TIME);
            int binningCount = data.getInt(ALIAS_TOTAL_COUNT);
            float binningCalories = data.getFloat(HealthConstants.StepCount.CALORIE);

            if (binningTime != null) {
              SimpleDateFormat dFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
              Date binDate = new Date();
              try {
                binDate = dFormat.parse(binningTime);
              } catch (ParseException e) {
                e.printStackTrace();
              }

              binningCountArray.add(new StepBinningData(binDate.getTime(), binningCount, binningCalories, 0));
            }
          }

          if (mObserver != null) {
            mObserver.onBinningDataChanged(binningCountArray);
          }

        } finally {
          result.close();
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Getting step binning data fails.", e);
    }
  }

  public interface StepCountObserver {
    void onChanged(long startTime, int count, float totalCalories);

    void onBinningDataChanged(int totalStepCount, float totalCalories, List<StepBinningData> binningCountList);

    default void onBinningDataChanged(List<StepBinningData> binningCountList) {
      Log.i(TAG, "onBinningDataChanged Size: " + binningCountList.size());
      Log.i(TAG, "onBinningDataChanged toString: " + binningCountList.toString());
    }
  }

  public static class StepBinningData implements Comparator<StepBinningData> {
    public final int count;
    public long time;
    private float calorie;
    private float distance;

    public StepBinningData(long time, int count) {
      this.time = time;
      this.count = count;
    }

    public StepBinningData(long time, int count, float calorie, float distance) {
      this.time = time;
      this.count = count;
      this.calorie = calorie;
      this.distance = distance;
    }

    public int getCount() {
      return count;
    }

    public long getTime() {
      return time;
    }

    public float getCalorie() {
      return calorie;
    }

    public float getDistance() {
      return distance;
    }

    @Override
    public int compare(StepBinningData o1, StepBinningData o2) {
      Long time1 = o1.getTime();
      Long time2 = o2.getTime();
      return time1.compareTo(time2);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      StepBinningData binItem = (StepBinningData) obj;
      return binItem.getTime() == this.getTime() && binItem.getCount() == this.getCount();
    }

    @Override
    public String toString() {
      return "StepBinningData{" +
       "count=" + count +
       ", time=" + time +
       ", calorie=" + calorie +
       ", distance=" + distance +
       '}';
    }
  }
}
