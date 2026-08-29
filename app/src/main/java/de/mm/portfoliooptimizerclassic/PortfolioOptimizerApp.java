package de.mm.portfoliooptimizerclassic;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Forces light mode globally so the app's light-only colour scheme
 * is never overridden by the system dark-mode setting, and loads the stored
 * portfolio before any activity can touch it.
 */
public class PortfolioOptimizerApp extends Application {
    @Override
    public void onCreate() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate();
        // Android can restart the app straight into any activity. Loading here
        // means none of them can ever run against an empty portfolio and then
        // save that emptiness over the stored one.
        Portfolio.getInstance().ensureLoaded(this);
    }
}

