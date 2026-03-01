package com.example.optimizer;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to interact with Alpha Vantage API.
 * Handles API key verification, security searching, and portfolio syncing with detailed error reporting.
 */
public class AlphaVantageService {
    private static final String BASE_URL = "https://www.alphavantage.co/query?";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    /**
     * Checks if the provided API key is valid.
     */
    public void checkKey(String apiKey, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String urlString = BASE_URL + "function=SYMBOL_SEARCH&keywords=AAPL&apikey=" + apiKey;
                String response = makeRequest(urlString);
                JsonObject json = gson.fromJson(response, JsonObject.class);

                if (json.has("Note")) {
                    mainHandler.post(() -> callback.onError("Rate limit reached (Note). Please wait a minute."));
                } else if (json.has("Information")) {
                    mainHandler.post(() -> callback.onError("API Info: " + json.get("Information").getAsString()));
                } else if (json.has("Error Message")) {
                    mainHandler.post(() -> callback.onSuccess(false));
                } else if (json.has("bestMatches")) {
                    mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    mainHandler.post(() -> callback.onSuccess(false));
                }
            } catch (IOException e) {
                handleIOException(e, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Unexpected error: " + e.getMessage()));
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

                if (searchJson.has("Note")) {
                    mainHandler.post(() -> callback.onError("Rate limit reached. Could not search for " + identifier));
                    return;
                }
                
                if (searchJson.has("Information")) {
                    mainHandler.post(() -> callback.onError("API Info: " + searchJson.get("Information").getAsString()));
                    return;
                }

                JsonArray matches = searchJson.getAsJsonArray("bestMatches");
                if (matches == null || matches.size() == 0) {
                    mainHandler.post(() -> callback.onError("Database does not contain the selected asset: " + identifier));
                    return;
                }

                JsonObject bestMatch = matches.get(0).getAsJsonObject();
                String symbol = bestMatch.get("1. symbol").getAsString();
                String name = bestMatch.get("2. name").getAsString();

                // 2. Fetch Daily Time Series data
                String dataUrl = BASE_URL + "function=TIME_SERIES_DAILY&symbol=" + symbol + "&outputsize=compact&apikey=" + apiKey;
                String dataResponse = makeRequest(dataUrl);
                JsonObject dataJson = gson.fromJson(dataResponse, JsonObject.class);

                if (dataJson.has("Note")) {
                    mainHandler.post(() -> callback.onError("Rate limit reached while fetching data for " + symbol));
                    return;
                }

                if (dataJson.has("Information")) {
                    mainHandler.post(() -> callback.onError("API Info: " + dataJson.get("Information").getAsString()));
                    return;
                }

                if (dataJson.has("Error Message")) {
                    mainHandler.post(() -> callback.onError("API Error: " + dataJson.get("Error Message").getAsString()));
                    return;
                }

                JsonObject timeSeries = dataJson.getAsJsonObject("Time Series (Daily)");
                if (timeSeries == null) {
                    mainHandler.post(() -> callback.onError("No time series data found for " + symbol + ". Check if it's a premium symbol."));
                    return;
                }

                Security security = new Security(name, identifier, quantity);
                populateSecurityData(security, timeSeries);

                mainHandler.post(() -> callback.onSuccess(security));

            } catch (IOException e) {
                handleIOException(e, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Unexpected error: " + e.getMessage()));
            }
        });
    }

    /**
     * Syncs the entire portfolio. 
     */
    public void syncPortfolio(Portfolio portfolio, String apiKey, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                List<Security> securities = portfolio.getSecurities();
                for (Security security : securities) {
                    String searchUrl = BASE_URL + "function=SYMBOL_SEARCH&keywords=" + security.getIdentifier() + "&apikey=" + apiKey;
                    String searchResponse = makeRequest(searchUrl);
                    JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);
                    
                    if (searchJson.has("Note") || searchJson.has("Information")) {
                        mainHandler.post(() -> callback.onError("Sync paused: Rate limit reached or API info received."));
                        return;
                    }

                    JsonArray matches = searchJson.getAsJsonArray("bestMatches");
                    if (matches != null && matches.size() > 0) {
                        String symbol = matches.get(0).getAsJsonObject().get("1. symbol").getAsString();
                        
                        String dataUrl = BASE_URL + "function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey;
                        String dataResponse = makeRequest(dataUrl);
                        JsonObject dataJson = gson.fromJson(dataResponse, JsonObject.class);
                        
                        if (dataJson.has("Note") || dataJson.has("Information")) {
                            mainHandler.post(() -> callback.onError("Sync paused: Rate limit reached or API info received."));
                            return;
                        }

                        JsonObject timeSeries = dataJson.getAsJsonObject("Time Series (Daily)");
                        if (timeSeries != null) {
                            populateSecurityData(security, timeSeries);
                        }
                    }
                    
                    Thread.sleep(15000);
                }
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (IOException e) {
                handleIOException(e, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Sync failed: " + e.getMessage()));
            }
        });
    }

    private void handleIOException(IOException e, Callback<?> callback) {
        if (e instanceof UnknownHostException) {
            mainHandler.post(() -> callback.onError("No internet connection. Please check your network."));
        } else {
            mainHandler.post(() -> callback.onError("Could not connect to the database (Alpha Vantage)."));
        }
    }

    private void populateSecurityData(Security security, JsonObject timeSeries) {
        List<Double> values = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        List<String> sortedDates = new ArrayList<>(timeSeries.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            JsonObject dayData = timeSeries.getAsJsonObject(date);
            double close = dayData.get("4. close").getAsDouble();
            values.add(close);
            dates.add(date);
        }

        security.setValuesOverTime(values);
        security.setDates(dates);
    }

    private String makeRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

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
