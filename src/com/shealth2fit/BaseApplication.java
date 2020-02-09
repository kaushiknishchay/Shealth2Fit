package com.shealth2fit;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import java.util.concurrent.Executors;

public class BaseApplication extends Application implements Configuration.Provider {
  private static Context context;

  public static Context getAppContext() {
    return BaseApplication.context;
  }

  public void onCreate() {
    super.onCreate();
    BaseApplication.context = getApplicationContext();
  }

  @NonNull
  @Override
  public Configuration getWorkManagerConfiguration() {
    return new Configuration.Builder()
     .setMinimumLoggingLevel(android.util.Log.INFO)
     .setExecutor(Executors.newFixedThreadPool(8))
     .build();
  }
}
