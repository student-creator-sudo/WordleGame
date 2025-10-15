package com.example.wordle;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.example.wordle.BarChartView;
import android.view.View;
import android.widget.EditText;
import java.util.Random;

public class StatisticsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_statistics);
        View root = findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView totalGamesText = findViewById(R.id.textViewTotalGames);
        TextView winsText = findViewById(R.id.textViewWins);
        TextView lossesText = findViewById(R.id.textViewLosses);
        TextView winRateText = findViewById(R.id.textViewWinRate);
        TextView lossRateText = findViewById(R.id.textViewLossRate);
        BarChartView barChartView = findViewById(R.id.barChartView);
        android.widget.ImageButton buttonGoBack = findViewById(R.id.buttonGoBack);
        android.widget.ImageButton buttonResetStats = findViewById(R.id.buttonResetStats);

        SharedPreferences prefs = getSharedPreferences("wordle_stats", MODE_PRIVATE);
        int totalGames = prefs.getInt("total_games", 0);
        int wins = prefs.getInt("wins", 0);
        int losses = prefs.getInt("losses", 0);
        int[] attemptsDist = new int[7]; // 7 pillars: [fails, solved_in_1, ..., solved_in_6]
        int attemptsSum = 0;
        attemptsDist[0] = losses; // First pillar is fails
        for (int i = 1; i < 7; i++) {
            attemptsDist[i] = prefs.getInt("solved_in_" + i, 0);
            attemptsSum += attemptsDist[i];
        }
        // Defensive: totalGames = wins + losses
        if (totalGames != wins + losses) totalGames = wins + losses;
        float winRate = totalGames > 0 ? (wins * 100f / totalGames) : 0f;
        float lossRate = totalGames > 0 ? (losses * 100f / totalGames) : 0f;

        totalGamesText.setText("Total Games: " + totalGames);
        winsText.setText("Wins: " + wins);
        lossesText.setText("Losses: " + losses);
        winRateText.setText(String.format("%.0f%% win rate", winRate));
        lossRateText.setText(String.format("%.0f%% loss rate", lossRate));
        barChartView.setValues(attemptsDist);

        buttonGoBack.setOnClickListener(v -> finish());
        buttonResetStats.setOnClickListener(v -> showResetStatsDialog());
    }

    private void showResetStatsDialog() {
        // Generate a random 7-character code
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        String resetCode = code.toString();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_stats, null);
        TextView codeText = dialogView.findViewById(R.id.textViewResetCode);
        codeText.setText(resetCode);
        EditText input = dialogView.findViewById(R.id.editTextResetCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Reset Statistics")
            .setView(dialogView)
            .setPositiveButton("Reset", null)
            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
            .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String entered = input.getText().toString().trim().toUpperCase();
                if (entered.equals(resetCode)) {
                    // Wipe stats
                    getSharedPreferences("wordle_stats", MODE_PRIVATE).edit().clear().apply();
                    recreate();
                    dialog.dismiss();
                } else {
                    input.setError("Incorrect code");
                }
            });
        });
        dialog.show();
    }
}
