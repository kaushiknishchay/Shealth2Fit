package com.shealth2fit.util;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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

  private static Date getDateFromMillis(long milliSeconds) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(milliSeconds);
    return cal.getTime();
  }

  private static Date getDateFromUTCMillis(long utcMilliSeconds) {
    Calendar calendar = Calendar.getInstance();
    TimeZone localTimeZone = TimeZone.getDefault();
    calendar.setTimeInMillis(utcMilliSeconds - localTimeZone.getRawOffset());
    return calendar.getTime();
  }

  static String getDateStringFromMillis(long milliSeconds) {
    Date date = getDateFromMillis(milliSeconds);
    DateFormat dateFormat = SimpleDateFormat.getDateInstance();
    return dateFormat.format(date);
  }

  public static String getDateStringFromUTCMillis(long utcMilliSeconds) {
    Date date = getDateFromUTCMillis(utcMilliSeconds);
    DateFormat dateFormat = SimpleDateFormat.getDateInstance();
    return dateFormat.format(date);
  }

  public static Calendar toUTC(Calendar localDate) {
    TimeZone localDateTimeZone = localDate.getTimeZone();

    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis((localDate.getTimeInMillis() + localDateTimeZone.getRawOffset()));

    return calendar;
  }
}
