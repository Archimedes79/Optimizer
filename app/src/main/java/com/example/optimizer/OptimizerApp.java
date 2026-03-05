package com.example.optimizer;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Forces light mode globally so the app's light-only colour scheme
 * is never overridden by the system dark-mode setting.
 */
public class OptimizerApp extends Application {
    @Override
    public void onCreate() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate();
    }
}

