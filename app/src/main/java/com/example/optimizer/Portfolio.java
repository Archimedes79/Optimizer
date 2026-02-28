package com.example.optimizer;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Portfolio {
    private static Portfolio instance;
    private List<Security> securities;
    private static final int MAX_SECURITIES = 24;
    private static final String FILE_NAME = "portfolio.json";

    private Portfolio() {
        this.securities = new ArrayList<>();
    }

    public static synchronized Portfolio getInstance() {
        if (instance == null) {
            instance = new Portfolio();
        }
        return instance;
    }

    public void load(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            securities = new ArrayList<>();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Security>>() {}.getType();
            securities = gson.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (securities == null) {
            securities = new ArrayList<>();
        }
    }

    public void save(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(securities, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean addSecurity(Security security) {
        if (securities.size() < MAX_SECURITIES) {
            securities.add(security);
            return true;
        }
        return false;
    }

    public void removeSecurity(Security security) {
        securities.remove(security);
    }

    public List<Security> getSecurities() {
        return securities;
    }
}
