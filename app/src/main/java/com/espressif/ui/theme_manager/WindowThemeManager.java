package com.espressif.ui.theme_manager;

import static android.graphics.Color.TRANSPARENT;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import android.view.View;
import android.view.Window;

import com.espressif.rainmaker.R;
import com.google.android.material.color.MaterialColors;

public class WindowThemeManager {
  private final Context context;

  private static boolean isNoActionBarWindow = false;
  public WindowThemeManager(Context context, boolean NoActionBar) {
    this.context = context;
    isNoActionBarWindow = NoActionBar;
  }

  public boolean isDarkTheme() {
    int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    switch (currentNightMode){
      case Configuration.UI_MODE_NIGHT_NO:
      return false;
      case Configuration.UI_MODE_NIGHT_YES:
        return true;
    }
    return false;
  }

  public void applyWindowTheme(Window window) {
    int newStatusBarColor = ContextCompat.getColor(context, R.color.color_actionbar_bg);
    if (isNoActionBarWindow) {
      newStatusBarColor = ContextCompat.getColor(context, R.color.color_app_bg);
    }

    boolean lightBackground = isColorLight(
            MaterialColors.getColor(context, android.R.attr.colorBackground, Color.BLACK));

    View decorView = window.getDecorView();
    int newFlagStatusBar = lightBackground && VERSION.SDK_INT >= VERSION_CODES.M
            ? SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            : 0;
    int newFlagNavBar = lightBackground && VERSION.SDK_INT >= VERSION_CODES.O
            ? SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            : 0;

    window.setNavigationBarColor(newStatusBarColor);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.setNavigationBarDividerColor(newStatusBarColor);
    }
    window.setStatusBarColor(newStatusBarColor);
    int systemUiVisibility = newFlagStatusBar | newFlagNavBar;

    decorView.setSystemUiVisibility(systemUiVisibility);
  }

  // Determines if a color should be considered light or dark
  private static boolean isColorLight(@ColorInt int color) {
    return color != TRANSPARENT && ColorUtils.calculateLuminance(color) > 0.5;
  }
}
