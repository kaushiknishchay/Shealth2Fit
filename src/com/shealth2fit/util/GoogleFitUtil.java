package com.shealth2fit.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.shealth2fit.StepCountReader;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.shealth2fit.util.DateUtil.getDateStringFromMillis;

public class GoogleFitUtil {
  public static final long ONE_DAY = 24 * 60 * 60 * 1000;
  private final static String TAG = "GoogleFitUtil";

  public static Task<DataReadResponse> readHistoricData(Context mContext, long startTime) {
    Log.i(TAG, "ReadData : Initialize");

    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(startTime); // compute start of the day for the timestamp
    cal.add(Calendar.DATE, +1);
    long endTime = cal.getTimeInMillis();

    Log.i(TAG, "Range Start: " + getDateStringFromMillis(startTime));
    Log.i(TAG, "Range End: " + getDateStringFromMillis(endTime));

    DataReadRequest readRequest =
     new DataReadRequest.Builder()
      .read(DataType.TYPE_STEP_COUNT_DELTA)
      .bucketByTime(1, TimeUnit.DAYS)
      .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
      .build();

    return Fitness
     .getHistoryClient(mContext, Objects.requireNonNull(GoogleSignIn.getLastSignedInAccount(mContext)))
     .readData(readRequest);
//     .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
//       @Override
//       public void onComplete(@NonNull Task<DataReadResponse> task) {
//         Log.i(TAG, "ReadData : onComplete");
//         DataReadResponse daysStepData = task.getResult();
//
//         getBinDataFromResponse(daysStepData);
//         Log.i(TAG, "Read Data : Successful");
//       }
//     });
  }

  public static void insertMultiDataPoints(Context mContext, List<StepCountReader.StepBinningData> stepsData) {
    FitnessOptions fitnessOptions = FitnessOptions.builder()
     .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
     .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
     .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
     .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
     .build();

    Log.i(TAG, "MultiData : Insert Initialize");
    Log.i(TAG, "MultiData : Create Data Store");

    // Create a data source
    DataSource dataSource =
     new DataSource.Builder()
      .setAppPackageName(mContext)
      .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
      .setStreamName(TAG + " - step count")
      .setType(DataSource.TYPE_RAW)
      .build();

    DataSource calorieDataSource =
     new DataSource.Builder()
      .setAppPackageName(mContext)
      .setDataType(DataType.TYPE_CALORIES_EXPENDED)
      .setStreamName(TAG + " - calorie")
      .setType(DataSource.TYPE_RAW)
      .build();

    DataSet dataSet = DataSet.create(dataSource);
    DataSet calorieDataSet = DataSet.create(calorieDataSource);

    // Create and add Data Point for each bin item to dataSet
    for (StepCountReader.StepBinningData data : stepsData) {
      // Start and End time are 10 minutes apart.

      Calendar cal = Calendar.getInstance();
      long startTime = data.time;
      Date startDate = new Date(startTime);
      cal.setTime(startDate);
      long startSec = cal.getTimeInMillis();
      cal.add(Calendar.MINUTE, +10);
      long endTime = cal.getTimeInMillis();

      int stepCountDelta = data.count;

      if (stepCountDelta > 0) {
        DataPoint dataPoint = DataPoint.builder(dataSource)
         .setTimeInterval(startSec, endTime, TimeUnit.MILLISECONDS)
         .setField(Field.FIELD_STEPS, stepCountDelta)
         .build();

        try {
          dataSet.add(dataPoint);
          calorieDataSet.add(
           DataPoint.builder(calorieDataSource)
            .setTimeInterval(startSec, endTime, TimeUnit.MILLISECONDS)
            .setField(Field.FIELD_CALORIES, data.getCalorie())
            .build()
          );
        } catch (Exception e) {
          Log.i(TAG, "Failed to add Data: " + e);
        }
      }
    }

    // Then, invoke the History API to insert the data.
    Log.i(TAG, "MultiData : Inserting the dataset in the History API");
    Log.i(TAG, "MultiData : Data Points inserting : " + dataSet.getDataPoints().size());

    if (mContext != null) {
      Fitness.getHistoryClient(mContext, GoogleSignIn.getAccountForExtension(mContext, fitnessOptions))
       .insertData(dataSet)
       .addOnCompleteListener(
        new OnCompleteListener<Void>() {
          @Override
          public void onComplete(@NonNull Task<Void> task) {
            if (task.isSuccessful()) {
              // At this point, the data has been inserted and can be read.
              Log.i(TAG, "MultiData : Insert was successful!");
            } else {
              Log.e(TAG, "MultiData : There was a problem inserting the dataset.", task.getException());
            }
          }
        });

      Fitness.getHistoryClient(mContext, GoogleSignIn.getAccountForExtension(mContext, fitnessOptions))
       .insertData(calorieDataSet)
       .addOnCompleteListener(
        new OnCompleteListener<Void>() {
          @Override
          public void onComplete(@NonNull Task<Void> task) {
            if (task.isSuccessful()) {
              // At this point, the data has been inserted and can be read.
              Log.i(TAG, "Calories MultiData : Insert was successful!");
            } else {
              Log.e(TAG, "Calories MultiData : There was a problem inserting the dataset.", task.getException());
            }
          }
        });
    }
  }

  public static List<StepCountReader.StepBinningData> getBinDataFromResponse(DataReadResponse daysStepData) {
    List<StepCountReader.StepBinningData> binningDataList = new ArrayList<>();

    if (daysStepData != null) {
      List<Bucket> datBuckets = daysStepData.getBuckets();

      for (Bucket dataBucket : datBuckets) {
        List<DataSet> dataSets = dataBucket.getDataSets();

        for (DataSet dataSet : dataSets) {
          for (DataPoint dp : dataSet.getDataPoints()) {
            long binStartTime = dp.getStartTime(TimeUnit.MILLISECONDS);
            int binStepCount = 0;

            for (Field field : dp.getDataType().getFields()) {
              if (field.getName().equals(Field.FIELD_STEPS)) {
                binStepCount = dp.getValue(field).asInt();
                Log.i(TAG, field.toString());
              }
            }

            StepCountReader.StepBinningData newBinItem = new StepCountReader.StepBinningData(binStartTime, binStepCount);
            binningDataList.add(newBinItem);
          }
        }
      }
    }
    Log.i(TAG, "Read Data : bin " + binningDataList.size());
    return binningDataList;
  }
}
