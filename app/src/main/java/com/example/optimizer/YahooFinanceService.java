package com.example.optimizer;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to interact with Yahoo Finance (Unofficial API).
 * Handles security searching and portfolio syncing with currency conversion to EUR.
 */
public class YahooFinanceService {
    private static final String SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q=";
    private static final String CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public void addSecurity(String identifier, String fallbackName, int quantity, Callback<Security> callback) {
        executor.execute(() -> {
            // Step 1: Try searching by the primary identifier
            if (performSearchAndPopulate(identifier, null, null, quantity, callback)) {
                return;
            }

            // Step 2: Try searching by fallback name if provided
            if (fallbackName != null && !fallbackName.trim().isEmpty()) {
                if (performSearchAndPopulate(fallbackName, null, fallbackName, quantity, callback)) {
                    return;
                }
            }

            mainHandler.post(() -> callback.onError("Could not find security by ID or Name: " + identifier));
        });
    }

    /**
     * Unifies the search, mapping, and population logic for adding and syncing.
     */
    private boolean performSearchAndPopulate(String searchTerm, Security existingSecurity, String alias, int quantity, Callback<Security> callback) {
        try {
            String encodedSearch = URLEncoder.encode(searchTerm.trim(), "UTF-8");
            String searchResponse = makeRequest(SEARCH_URL + encodedSearch);
            JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);

            JsonArray quotes = searchJson.getAsJsonArray("quotes");
            if (quotes == null || quotes.size() == 0) return false;

            // Try the top few results to find one with valid chart data
            for (int i = 0; i < Math.min(quotes.size(), 8); i++) {
                JsonObject quote = quotes.get(i).getAsJsonObject();
                if (!quote.has("symbol")) continue;

                String symbol = quote.get("symbol").getAsString();
                String name = quote.has("shortname") ? quote.get("shortname").getAsString() : 
                             (quote.has("longname") ? quote.get("longname").getAsString() : symbol);

                // Priority mapping: ISIN -> Ticker (symbol) -> WKN
                String bestId = null;
                if (quote.has("isin")) {
                    bestId = quote.get("isin").getAsString();
                } else if (quote.has("symbol")) {
                    bestId = quote.get("symbol").getAsString();
                } else if (quote.has("wkn")) {
                    bestId = quote.get("wkn").getAsString();
                }

                if (bestId == null) continue;

                // Attempt to fetch data and populate the security object (one function to rule them all)
                if (tryPopulate(symbol, name, bestId, existingSecurity, alias, quantity, "20y", callback)) return true;
                if (tryPopulate(symbol, name, bestId, existingSecurity, alias, quantity, "max", callback)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean tryPopulate(String symbol, String name, String bestId, Security existingSecurity, String alias, int qty, String range, Callback<Security> callback) {
        try {
            String dataUrl = CHART_URL + URLEncoder.encode(symbol, "UTF-8") + "?range=" + range + "&interval=1mo";
            String dataResponse = makeRequest(dataUrl);
            JsonObject dataJson = gson.fromJson(dataResponse, JsonObject.class);

            Security security = (existingSecurity != null) ? existingSecurity : new Security();
            security.setName(name);
            security.setIdentifier(bestId);
            security.setQuantity(qty);
            if (alias != null) security.setAlias(alias);

            if (populateSecurityData(security, dataJson)) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(security));
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void syncPortfolio(Portfolio portfolio, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                List<Security> securities = portfolio.getSecurities();
                for (Security security : securities) {
                    // Use the unified search and populate logic to refresh data and IDs
                    performSearchAndPopulate(security.getIdentifier(), security, null, security.getQuantity(), null);
                    Thread.sleep(200); // Respect API rate limits
                }
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Sync failed: " + e.getMessage()));
            }
        });
    }

    private boolean populateSecurityData(Security security, JsonObject dataJson) {
        try {
            if (!dataJson.has("chart") || dataJson.getAsJsonObject("chart").getAsJsonArray("result") == null) return false;
            JsonObject result = dataJson.getAsJsonObject("chart").getAsJsonArray("result").get(0).getAsJsonObject();
            
            String currency = "USD";
            if (result.has("meta") && result.getAsJsonObject("meta").has("currency")) {
                currency = result.getAsJsonObject("meta").get("currency").getAsString();
            }

            JsonArray timestamps = result.getAsJsonArray("timestamp");
            if (timestamps == null) return false;

            JsonObject indicators = result.getAsJsonObject("indicators");
            JsonArray values = null;
            if (indicators.has("adjclose")) {
                JsonArray adjArray = indicators.getAsJsonArray("adjclose");
                if (adjArray != null && adjArray.size() > 0) {
                    values = adjArray.get(0).getAsJsonObject().getAsJsonArray("adjclose");
                }
            }
            if ((values == null || values.isJsonNull() || values.size() == 0) && indicators.has("quote")) {
                JsonArray quoteArray = indicators.getAsJsonArray("quote");
                if (quoteArray != null && quoteArray.size() > 0) {
                    values = quoteArray.get(0).getAsJsonObject().getAsJsonArray("close");
                }
            }
            if (values == null || values.size() == 0) return false;

            List<Double> rawPrices = new ArrayList<>();
            List<String> dates = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            int count = Math.min(timestamps.size(), values.size());
            for (int i = 0; i < count; i++) {
                if (!values.get(i).isJsonNull()) {
                    rawPrices.add(values.get(i).getAsDouble());
                    dates.add(sdf.format(new Date(timestamps.get(i).getAsLong() * 1000)));
                }
            }

            if (rawPrices.isEmpty()) return false;

            List<Double> euroPrices = convertToEuro(rawPrices, dates, currency);
            security.setValuesOverTime(euroPrices);
            security.setDates(dates);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<Double> convertToEuro(List<Double> prices, List<String> dates, String fromCurrency) {
        if (fromCurrency == null || fromCurrency.equalsIgnoreCase("EUR")) return prices;

        double unitFactor = fromCurrency.equalsIgnoreCase("GBp") ? 0.01 : 1.0;
        String normalizedFrom = fromCurrency.equalsIgnoreCase("GBp") ? "GBP" : fromCurrency.toUpperCase();
        String pair = normalizedFrom + "EUR=X";

        Map<String, Double> ratesByDate = new HashMap<>();
        try {
            String rateResponse = makeRequest(CHART_URL + pair + "?range=20y&interval=1mo");
            JsonObject rateJson = gson.fromJson(rateResponse, JsonObject.class);
            JsonObject result = rateJson.getAsJsonObject("chart").getAsJsonArray("result").get(0).getAsJsonObject();
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            JsonObject indicators = result.getAsJsonObject("indicators");
            JsonArray values = null;
            
            if (indicators.has("adjclose")) {
                values = indicators.getAsJsonArray("adjclose").get(0).getAsJsonObject().getAsJsonArray("adjclose");
            } else if (indicators.has("quote")) {
                values = indicators.getAsJsonArray("quote").get(0).getAsJsonObject().getAsJsonArray("close");
            }
            
            if (timestamps != null && values != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                int count = Math.min(timestamps.size(), values.size());
                for (int i = 0; i < count; i++) {
                    if (!values.get(i).isJsonNull()) {
                        ratesByDate.put(sdf.format(new Date(timestamps.get(i).getAsLong() * 1000)), values.get(i).getAsDouble());
                    }
                }
            }
        } catch (Exception ignored) {}

        List<Double> converted = new ArrayList<>();
        double lastKnownRate = 1.0; 
        for (int i = 0; i < prices.size(); i++) {
            Double rate = ratesByDate.get(dates.get(i));
            if (rate != null) lastKnownRate = rate;
            converted.add(prices.get(i) * unitFactor * lastKnownRate);
        }
        return converted;
    }

    private String makeRequest(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Server returned HTTP " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        } finally {
            conn.disconnect();
        }
    }
}
