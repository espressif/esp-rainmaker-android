package com.espressif.ui.theme_manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

public class AppThemeManager {
  public static final String PREFERENCES_NAME = "night_mode_preferences";
  public static final String KEY_NIGHT_MODE = "night_mode";
  public static final String LIGHT_MODE = "light_mode";
  public static final String DARK_MODE = "dark_mode";
  public static final String DEFAULT_MODE = "default_mode";

  private final Context context;

  public AppThemeManager(Context context) {
    this.context = context;
  }

  public static void applyAppTheme(@NonNull String nightMode) {
    switch (nightMode) {
      case LIGHT_MODE: {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        break;
      }
      case DARK_MODE: {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        break;
      }
      default: {
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
        }
        break;
      }
    }
  }

  public boolean getAppTheme(){
    String prefNightMode = getNightMode();
    return prefNightMode.equals(DARK_MODE) || isNightMode();
  }

  public boolean isNightMode() {
    int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    switch (currentNightMode){
      case Configuration.UI_MODE_NIGHT_NO:
        return false;
      case Configuration.UI_MODE_NIGHT_YES:
        return true;
    }
    return false;
  }

  public void getAndApplyTheme() {
    String nightMode = getNightMode();
    applyAppTheme(nightMode);
  }

  public void saveAndApplyTheme(String nightMode) {
    saveNightMode(nightMode);
    applyAppTheme(nightMode);
  }

  private void saveNightMode(String nightMode) {
    getSharedPreferences().edit().putString(KEY_NIGHT_MODE, nightMode).apply();
  }

  private String getNightMode() {
    return getSharedPreferences()
            .getString(KEY_NIGHT_MODE, DEFAULT_MODE);
  }

  private SharedPreferences getSharedPreferences() {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }
}

