package com.shealth2fit.util;

import android.app.Activity;
import android.util.Log;

import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthResultHolder;
import com.shealth2fit.StepCountReader;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SamsungHealthUtil {
  private static final String TAG = "SamsungHealthUtil";

  /* Check if Samsung Health permissions given */
  public static boolean isPermissionAcquired(HealthDataStore mStore) {
    HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);
    try {
      // Check whether the permissions that this application needs are acquired
      Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = pmsManager.isPermissionAcquired(generatePermissionKeySet());
      return !resultMap.values().contains(Boolean.FALSE);
    } catch (Exception e) {
      Log.e(TAG, "Permission request fails.", e);
    }
    return false;
  }

  /* What permissions to be asked for based on data */
  private static Set<HealthPermissionManager.PermissionKey> generatePermissionKeySet() {
    Set<HealthPermissionManager.PermissionKey> pmsKeySet = new HashSet<>();
    pmsKeySet.add(new HealthPermissionManager.PermissionKey(HealthConstants.StepCount.HEALTH_DATA_TYPE, HealthPermissionManager.PermissionType.READ));
    pmsKeySet.add(new HealthPermissionManager.PermissionKey(StepCountReader.STEP_SUMMARY_DATA_TYPE_NAME, HealthPermissionManager.PermissionType.READ));
    pmsKeySet.add(new HealthPermissionManager.PermissionKey(HealthConstants.Sleep.HEALTH_DATA_TYPE, HealthPermissionManager.PermissionType.READ));
    return pmsKeySet;
  }

  /* Ask for the permissions */
  public static void requestPermission(HealthDataStore mStore, Activity mActivity, HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult> mPermissionListener) {
    HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);
    try {
      // Show user permission UI for allowing user to change options
      pmsManager.requestPermissions(generatePermissionKeySet(), mActivity)
       .setResultListener(mPermissionListener);
    } catch (Exception e) {
      Log.e(TAG, "Permission setting fails.", e);
    }
  }

}
