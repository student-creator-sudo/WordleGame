package com.example.wordle;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Calendar;




public class HomeActivity extends AppCompatActivity {

    private Button buttonSinglePlayer;
    private Button buttonMultiplayer;
    private SwitchCompat switchDailyAlarm;

    // No need for GAME_ACTIVITY_CLASS_NAME if we directly reference GameActivity.class
    // private final String GAME_ACTIVITY_CLASS_NAME = "com.example.wordle.GameActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); // Force light mode
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home2); // This MUST match your XML file name

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        buttonSinglePlayer = findViewById(R.id.buttonSinglePlayer);
        buttonMultiplayer = findViewById(R.id.buttonMultiplayer);
        switchDailyAlarm = findViewById(R.id.switch_daily_alarm);

        SharedPreferences prefs = getSharedPreferences("wordle_prefs", MODE_PRIVATE);
        boolean alarmEnabled = prefs.getBoolean("daily_alarm_enabled", false);
        switchDailyAlarm.setChecked(alarmEnabled);
        switchDailyAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("daily_alarm_enabled", isChecked);
            editor.apply();
            if (isChecked) {
                scheduleDailyAlarm();
                Toast.makeText(this, "Daily reminder enabled", Toast.LENGTH_SHORT).show();
            } else {
                cancelDailyAlarm();
                Toast.makeText(this, "Daily reminder disabled", Toast.LENGTH_SHORT).show();
            }
        });
        if (alarmEnabled) {
            scheduleDailyAlarm();
        }

        buttonSinglePlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Directly reference GameActivity.class
                Intent intent = new Intent(HomeActivity.this, GameActivity.class);
                intent.putExtra("GAME_MODE", "SINGLE_PLAYER");
                startActivity(intent);
                Log.d("HomeActivity", "Attempting to start GameActivity for SINGLE_PLAYER.");
            }
        });


        buttonMultiplayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, MultiplayerActivity.class);
                startActivity(intent);
                Log.d("HomeActivity", "Attempting to start MultiplayerActivity.");
            }
        });
    }

    private void scheduleDailyAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailyAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long triggerTime = calendar.getTimeInMillis();
        if (System.currentTimeMillis() > triggerTime) {
            triggerTime += AlarmManager.INTERVAL_DAY;
        }
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent);
    }

    private void cancelDailyAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailyAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }
}