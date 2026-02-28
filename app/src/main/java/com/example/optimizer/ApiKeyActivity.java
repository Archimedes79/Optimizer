package com.example.optimizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class ApiKeyActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "OptimizerPrefs";
    private static final String KEY_API_KEY = "api_key";

    private EditText etApiKey;
    private Button btnTestKey;
    private Button btnSaveKey;
    private Button btnCancel;
    private ProgressBar pbTesting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_key);

        // Enable the back button in the title bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("API Key Settings");
        }

        etApiKey = findViewById(R.id.etApiKey);
        btnTestKey = findViewById(R.id.btnTestKey);
        btnSaveKey = findViewById(R.id.btnSaveKey);
        btnCancel = findViewById(R.id.btnCancel);
        pbTesting = findViewById(R.id.pbTesting);

        // Load existing key if any
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_API_KEY, "");
        etApiKey.setText(savedKey);

        btnTestKey.setOnClickListener(v -> testApiKey());
        btnSaveKey.setOnClickListener(v -> saveApiKey());
        btnCancel.setOnClickListener(v -> finish());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle the back arrow in the action bar
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void testApiKey() {
        String key = etApiKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "Please enter a key", Toast.LENGTH_SHORT).show();
            return;
        }

        pbTesting.setVisibility(View.VISIBLE);
        btnTestKey.setEnabled(false);

        // Simulate network call to test API key
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pbTesting.setVisibility(View.GONE);
            btnTestKey.setEnabled(true);
            
            // For now, any non-empty key longer than 5 chars is "valid"
            if (key.length() > 5) {
                Toast.makeText(this, "API Key is valid!", Toast.LENGTH_SHORT).show();
                btnSaveKey.setEnabled(true);
            } else {
                Toast.makeText(this, "Invalid API Key. Try a longer one.", Toast.LENGTH_SHORT).show();
                btnSaveKey.setEnabled(false);
            }
        }, 2000);
    }

    private void saveApiKey() {
        String key = etApiKey.getText().toString().trim();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_KEY, key).apply();

        Toast.makeText(this, "API Key saved successfully", Toast.LENGTH_SHORT).show();
        finish(); // Go back to MainActivity
    }
}
