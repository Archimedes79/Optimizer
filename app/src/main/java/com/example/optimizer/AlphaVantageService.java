package com.example.optimizer;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to interact with Alpha Vantage API.
 * Handles API key verification, security searching, and portfolio syncing.
 */
public class AlphaVantageService {
    private static final String BASE_URL = "https://www.alphavantage.co/query?";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /**
     * Checks if the provided API key is valid.
     */
    public void checkKey(String apiKey, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                // Using a simple search query to validate the key
                String urlString = BASE_URL + "function=SYMBOL_SEARCH&keywords=AAPL&apikey=" + apiKey;
                String response = makeRequest(urlString);
                JsonObject json = gson.fromJson(response, JsonObject.class);

                // Alpha Vantage returns "Note" for rate limits and "Error Message" for invalid keys
                if (json.has("Error Message") || json.has("Note")) {
                    mainHandler.post(() -> callback.onSuccess(false));
                } else if (json.has("bestMatches")) {
                    mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    mainHandler.post(() -> callback.onSuccess(false));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Fetches data for a new security using WKN or ISIN.
     */
    public void addSecurity(String identifier, int quantity, String apiKey, Callback<Security> callback) {
        executor.execute(() -> {
            try {
                // 1. Search for the symbol corresponding to the WKN/ISIN
                String searchUrl = BASE_URL + "function=SYMBOL_SEARCH&keywords=" + identifier + "&apikey=" + apiKey;
                String searchResponse = makeRequest(searchUrl);
                JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);
                JsonArray matches = searchJson.getAsJsonArray("bestMatches");

                if (matches == null || matches.size() == 0) {
                    mainHandler.post(() -> callback.onError(new Exception("No results found for " + identifier)));
                    return;
                }

                JsonObject bestMatch = matches.get(0).getAsJsonObject();
                String symbol = bestMatch.get("1. symbol").getAsString();
                String name = bestMatch.get("2. name").getAsString();

                // 2. Fetch Daily Time Series data
                String dataUrl = BASE_URL + "function=TIME_SERIES_DAILY&symbol=" + symbol + "&outputsize=compact&apikey=" + apiKey;
                String dataResponse = makeRequest(dataUrl);
                JsonObject dataJson = gson.fromJson(dataResponse, JsonObject.class);

                if (dataJson.has("Error Message")) {
                    mainHandler.post(() -> callback.onError(new Exception(dataJson.get("Error Message").getAsString())));
                    return;
                }

                JsonObject timeSeries = dataJson.getAsJsonObject("Time Series (Daily)");
                if (timeSeries == null) {
                    mainHandler.post(() -> callback.onError(new Exception("No time series data found for " + symbol)));
                    return;
                }

                Security security = new Security(name, identifier, quantity);
                populateSecurityData(security, timeSeries);

                mainHandler.post(() -> callback.onSuccess(security));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Syncs the entire portfolio. 
     * Note: Respects rate limits by adding delays between requests.
     */
    public void syncPortfolio(Portfolio portfolio, String apiKey, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                List<Security> securities = portfolio.getSecurities();
                for (Security security : securities) {
                    // Search symbol for identifier
                    String searchUrl = BASE_URL + "function=SYMBOL_SEARCH&keywords=" + security.getIdentifier() + "&apikey=" + apiKey;
                    String searchResponse = makeRequest(searchUrl);
                    JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);
                    JsonArray matches = searchJson.getAsJsonArray("bestMatches");

                    if (matches != null && matches.size() > 0) {
                        String symbol = matches.get(0).getAsJsonObject().get("1. symbol").getAsString();
                        
                        // Fetch data
                        String dataUrl = BASE_URL + "function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey;
                        String dataResponse = makeRequest(dataUrl);
                        JsonObject dataJson = gson.fromJson(dataResponse, JsonObject.class);
                        JsonObject timeSeries = dataJson.getAsJsonObject("Time Series (Daily)");
                        
                        if (timeSeries != null) {
                            populateSecurityData(security, timeSeries);
                        }
                    }
                    
                    // Delay to stay within free tier rate limit (5 calls/min)
                    Thread.sleep(15000); 
                }
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private void populateSecurityData(Security security, JsonObject timeSeries) {
        List<Double> values = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        List<String> sortedDates = new ArrayList<>(timeSeries.keySet());
        Collections.sort(sortedDates); // Past to Present

        for (String date : sortedDates) {
            JsonObject dayData = timeSeries.getAsJsonObject(date);
            double close = dayData.get("4. close").getAsDouble();
            values.add(close);
            dates.add(date);
        }

        security.setValuesOverTime(values);
        // Ensure Security class has setDates or handles date mapping if needed
    }

    private String makeRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } finally {
            conn.disconnect();
        }
    }
}
