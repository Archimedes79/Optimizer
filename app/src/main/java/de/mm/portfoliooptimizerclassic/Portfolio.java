package de.mm.portfoliooptimizerclassic;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that holds every security in the portfolio.
 *
 * <p>Persists via Gson JSON.  Whenever the list changes
 * ({@link #addSecurity}, {@link #removeSecurity}, {@link #load}),
 * {@link #recalculateCommonRange()} is called so that every Security's
 * startIndex / endIndex point to the shared date window.</p>
 */
public class Portfolio {
    private static final String TAG = "Portfolio";
    private static Portfolio instance;
    private final List<Security> securities = new ArrayList<>();
    private boolean loaded = false;
    private static final int MAX_SECURITIES = 24;
    private static final String FILE_NAME = "portfolio.json";

    private Portfolio() {
    }

    public static synchronized Portfolio getInstance() {
        if (instance == null) {
            instance = new Portfolio();
        }
        return instance;
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /**
     * Loads the portfolio once per process.
     *
     * <p>Every entry point should call this rather than {@link #load}: Android can
     * restart the app directly into any activity after killing the process, and an
     * activity that finds an empty singleton would overwrite the stored file on its
     * next save.</p>
     */
    public synchronized void ensureLoaded(Context context) {
        if (!loaded) load(context);
    }

    /** Loads securities from JSON and recalculates the common range. */
    public synchronized void load(Context context) {
        loaded = true;
        File file = new File(context.getFilesDir(), FILE_NAME);
        List<Security> parsed = null;

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<ArrayList<Security>>() {}.getType();
                parsed = new Gson().fromJson(reader, type);
            } catch (IOException | JsonIOException e) {
                // A read error says nothing about the file's contents - keep it.
                Log.e(TAG, "Could not read " + FILE_NAME, e);
            } catch (JsonSyntaxException e) {
                // Malformed JSON would crash the app on every start. Move it aside.
                Log.e(TAG, "Corrupt " + FILE_NAME + " - starting with an empty portfolio", e);
                quarantine(file);
            }
        }

        // Replace the contents, not the list instance: a sync running in the
        // background holds a reference to this list.
        securities.clear();
        if (parsed != null) {
            for (Security s : parsed) {
                if (s != null) securities.add(s);
            }
        }
        // transient indices are 0 after deserialisation – fix them
        recalculateCommonRange();
    }

    /**
     * Saves the current list to the internal JSON file.
     *
     * <p>Writes to a temporary file first and only then replaces the previous
     * one, so an interrupted write can never leave an unreadable portfolio
     * behind.</p>
     */
    public synchronized void save(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        File tmp  = new File(context.getFilesDir(), FILE_NAME + ".tmp");

        try (FileWriter writer = new FileWriter(tmp)) {
            new Gson().toJson(securities, writer);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Could not write " + FILE_NAME, e);
            deleteQuietly(tmp);
            return;
        }

        if (!tmp.renameTo(file)) {
            Log.e(TAG, "Could not replace " + FILE_NAME);
            deleteQuietly(tmp);
        }
    }

    /** Moves an unreadable portfolio file aside so it is never parsed again. */
    private void quarantine(File file) {
        File backup = new File(file.getParentFile(), FILE_NAME + ".corrupt");
        deleteQuietly(backup);
        if (!file.renameTo(backup)) {
            deleteQuietly(file);
        }
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete " + file.getName());
        }
    }

    // ── Add / Remove ────────────────────────────────────────────────────────

    /** Adds a security (max 24) and recalculates the common date range. */
    public synchronized boolean addSecurity(Security security) {
        if (securities.size() < MAX_SECURITIES) {
            securities.add(security);
            recalculateCommonRange();
            return true;
        }
        return false;
    }

    /** Removes a security and recalculates the common date range. */
    public synchronized void removeSecurity(Security security) {
        securities.remove(security);
        recalculateCommonRange();
    }

    public List<Security> getSecurities() {
        return securities;
    }

    /**
     * Defensive copy for iteration off the UI thread.
     *
     * <p>Iterating {@link #getSecurities()} from a background thread throws a
     * ConcurrentModificationException as soon as the user adds or removes a
     * position while that loop is running.</p>
     */
    public synchronized List<Security> getSecuritiesSnapshot() {
        return new ArrayList<>(securities);
    }

    /** Maximum number of securities the optimiser is dimensioned for. */
    public static int getMaxSecurities() {
        return MAX_SECURITIES;
    }

    // ── Common-range calculation ────────────────────────────────────────────

    /**
     * Scans all securities for max(firstDay) and min(lastDay), then sets
     * startIndex / endIndex on every security via binary search.
     *
     * <p>Called automatically by add / remove / load.  Call manually after a
     * sync that replaces underlying data arrays.</p>
     */
    public synchronized void recalculateCommonRange() {
        if (securities.isEmpty()) return;

        int maxStart = Integer.MIN_VALUE;
        int minEnd   = Integer.MAX_VALUE;

        for (Security s : securities) {
            if (s == null) continue;
            int[] days = s.getEpochDays();
            if (days == null || days.length == 0) continue;
            maxStart = Math.max(maxStart, days[0]);
            minEnd   = Math.min(minEnd,   days[days.length - 1]);
        }

        if (maxStart > minEnd) {
            // No overlap – reset every security to its full range
            Log.w(TAG, "No overlapping date range – using full ranges");
            for (Security s : securities) {
                if (s == null) continue;
                int[] days = s.getEpochDays();
                if (days != null && days.length > 0) {
                    s.setStartIndex(days[0]);
                    s.setEndIndex(days[days.length - 1]);
                }
            }
            return;
        }

        for (Security s : securities) {
            if (s == null || s.getEpochDays() == null || s.getEpochDays().length == 0) continue;
            s.setStartIndex(maxStart);
            s.setEndIndex(minEnd);
        }
        Log.d(TAG, "Common range: day " + maxStart + "–" + minEnd
                + " (" + (minEnd - maxStart + 1) + " days)");
    }
}
