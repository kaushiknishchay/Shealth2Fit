package com.shealth2fit.util;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtil {
  private static String TAG = "DateUtil";
  public static long TODAY_START_UTC_TIME;

  static {
    TODAY_START_UTC_TIME = getTodayStartUtcTime();
  }

  private static long getTodayStartUtcTime() {
    Calendar today = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    Log.d(TAG, "Today : " + today.getTimeInMillis());

    today.set(Calendar.HOUR_OF_DAY, 0);
    today.set(Calendar.MINUTE, 0);
    today.set(Calendar.SECOND, 0);
    today.set(Calendar.MILLISECOND, 0);

    return today.getTimeInMillis();
  }

  public static Date getDateFromMillis(long milliSeconds) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(milliSeconds);
    return cal.getTime();
  }

  public static String getDateStringFromMillis(long milliSeconds) {
    Date date = getDateFromMillis(milliSeconds);
    SimpleDateFormat dateFormat = new SimpleDateFormat(" dd MMM yyyy ", Locale.getDefault());
    return dateFormat.format(date);
  }

}
