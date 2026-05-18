package com.example.wordle;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.ImageButton;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Intent;

import android.content.res.AssetManager;
import java.io.*;
import java.util.*;

import android.widget.TextView;

import android.graphics.Typeface;

import android.widget.Toast;
import android.view.MotionEvent;
import android.content.SharedPreferences;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import android.os.CountDownTimer;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GameActivity extends AppCompatActivity {

    private WordleGameState gameState;
    private StringBuilder currentGuess = new StringBuilder();
    private Set<String> allowedWords = new HashSet<>();
    private final Map<Character, Integer> keyColors = new HashMap<>();

    // Add these fields at the top of the class
    private boolean includeAllWords = false; // Default: switch is off, use WordleStandard.txt
    private boolean hardMode = false; // Default: switch is off
    private List<String> standardWordList = new ArrayList<>();
    private List<String> allWordList = new ArrayList<>();

    private Toast currentToast;

    private String cachedHint = null; // Store hint for the current game

    // Multiplayer mode flag
    private boolean isMultiplayer = false;

    // Multiplayer fields
    private CountDownTimer gameCountDownTimer;
    private static final long GAME_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private TextView countdownTextView;
    private FirebaseFirestore db;
    private String duelId; // Unique ID for this duel session
    private String myUid;
    private String opponentUid;
    private ListenerRegistration duelListener;
    private boolean hasSubmittedResult = false;
    private String myFinalVerdict = null;
    private String targetWord = null;

    private AlertDialog waitingDialog;

    // Add a boolean flag to prevent multiple countdowns
    private boolean CDHasStarted = false;

    private static final String TAG = "GameActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_game);

        // Check if multiplayer mode is activated via Intent extra
        Intent intent = getIntent();
        isMultiplayer = intent.getBooleanExtra("isMultiplayer", false);
        Log.d(TAG, "onCreate: isMultiplayer=" + isMultiplayer);
        if (isMultiplayer) {
            myUid = intent.getStringExtra("myUid");
            opponentUid = intent.getStringExtra("opponentUid");
            duelId = intent.getStringExtra("duelId");
            Log.d(TAG, "onCreate: myUid=" + myUid + " opponentUid=" + opponentUid + " duelId=" + duelId);
            db = FirebaseFirestore.getInstance();
            db.collection("users").document(myUid).update("inGame", true);
            Log.d(TAG, "onCreate: set inGame=true for " + myUid);
            // Always use hard mode and WordleStandard.txt for multiplayer
            includeAllWords = false;
            hardMode = true;
        } else {
            // Load preferences from SharedPreferences for single-player mode
            SharedPreferences prefs = getSharedPreferences("WordlePrefs", MODE_PRIVATE);
            hardMode = prefs.getBoolean("hardMode", false);
            includeAllWords = prefs.getBoolean("includeAllWords", false);
        }

        // Load both word lists at startup
        loadWordLists();

        // Start a new game with the correct word list
        if (isMultiplayer) {
            Log.d(TAG, "onCreate: fetching/setting target word for duel " + duelId);
            fetchOrSetTargetWord();
        } else {
            startNewGame();
        }

        ImageButton optionsButton = findViewById(R.id.buttonOptions);
        ImageButton statisticsButton = findViewById(R.id.buttonStatistics);
        ImageButton forfeitButton = findViewById(R.id.buttonForfeit);
        forfeitButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Forfeit Match")
                    .setMessage("Are you sure you want to forfeit? This will count as a loss.")
                    .setPositiveButton("Forfeit", (dialog, which) -> endGameWithVerdict("forfeit"))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
        countdownTextView = findViewById(R.id.textViewCountdown);

        optionsButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_options, null);
            builder.setView(dialogView);

            // Home button
            ImageButton buttonHome = dialogView.findViewById(R.id.buttonHome);
            buttonHome.setOnClickListener(view -> {
                Intent intent1 = new Intent(GameActivity.this, HomeActivity.class);
                intent1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent1);
                finish();
            });

            // Switches
            SwitchCompat switchHardMode = dialogView.findViewById(R.id.switchHardMode);
            SwitchCompat switchIncludeAllWords = dialogView.findViewById(R.id.IncludeALlWordsSwitch);

            // Set initial state for switches
            switchIncludeAllWords.setChecked(includeAllWords);
            switchHardMode.setChecked(hardMode);

            switchHardMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                hardMode = isChecked;
                // Save the switch state to SharedPreferences
                getSharedPreferences("WordlePrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("hardMode", hardMode)
                        .apply();
                // Only apply to next game, not the current one
            });

            switchIncludeAllWords.setOnCheckedChangeListener((buttonView, isChecked) -> {
                includeAllWords = isChecked;
                // Save the switch state to SharedPreferences
                getSharedPreferences("WordlePrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("includeAllWords", includeAllWords)
                        .apply();
                // Only apply to next game, not the current one
            });

            // Add Restart button
            View restartButton = dialogView.findViewById(R.id.buttonRestart);
            if (restartButton != null) {
                restartButton.setOnClickListener(view -> {
                    restartGame();
                });
            }

            // Add Hint button
            View hintButton = dialogView.findViewById(R.id.buttonHint);
            if (hintButton != null) {
                hintButton.setOnClickListener(view -> {
                    builder.create().dismiss();
                    onHintButtonClicked();
                });
            }

            builder.setNegativeButton("Close", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        });


        // Use the ID that actually exists in your activity_game.xml
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_activity_main_layout), (v, insets) -> { // <<<< CORRECTED ID HERE
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });




        findViewById(R.id.key_a).setOnClickListener(v -> addLetter('A'));
        findViewById(R.id.key_b).setOnClickListener(v -> addLetter('B'));
        findViewById(R.id.key_c).setOnClickListener(v -> addLetter('C'));
        findViewById(R.id.key_d).setOnClickListener(v -> addLetter('D'));
        findViewById(R.id.key_e).setOnClickListener(v -> addLetter('E'));
        findViewById(R.id.key_f).setOnClickListener(v -> addLetter('F'));
        findViewById(R.id.key_g).setOnClickListener(v -> addLetter('G'));
        findViewById(R.id.key_h).setOnClickListener(v -> addLetter('H'));
        findViewById(R.id.key_i).setOnClickListener(v -> addLetter('I'));
        findViewById(R.id.key_j).setOnClickListener(v -> addLetter('J'));
        findViewById(R.id.key_k).setOnClickListener(v -> addLetter('K'));
        findViewById(R.id.key_l).setOnClickListener(v -> addLetter('L'));
        findViewById(R.id.key_m).setOnClickListener(v -> addLetter('M'));
        findViewById(R.id.key_n).setOnClickListener(v -> addLetter('N'));
        findViewById(R.id.key_o).setOnClickListener(v -> addLetter('O'));
        findViewById(R.id.key_p).setOnClickListener(v -> addLetter('P'));
        findViewById(R.id.key_q).setOnClickListener(v -> addLetter('Q'));
        findViewById(R.id.key_r).setOnClickListener(v -> addLetter('R'));
        findViewById(R.id.key_s).setOnClickListener(v -> addLetter('S'));
        findViewById(R.id.key_t).setOnClickListener(v -> addLetter('T'));
        findViewById(R.id.key_u).setOnClickListener(v -> addLetter('U'));
        findViewById(R.id.key_v).setOnClickListener(v -> addLetter('V'));
        findViewById(R.id.key_w).setOnClickListener(v -> addLetter('W'));
        findViewById(R.id.key_x).setOnClickListener(v -> addLetter('X'));
        findViewById(R.id.key_y).setOnClickListener(v -> addLetter('Y'));
        findViewById(R.id.key_z).setOnClickListener(v -> addLetter('Z'));


        findViewById(R.id.key_backspace).setOnClickListener(v -> removeLetter());
        findViewById(R.id.key_enter).setOnClickListener(v -> submitGuess());

        statisticsButton.setOnClickListener(v -> {
            Intent statsIntent = new Intent(GameActivity.this, StatisticsActivity.class);
            startActivity(statsIntent);
        });






        // Set visibility and position of controls based on multiplayer mode
        if (isMultiplayer) {
            // Hide options and statistics buttons
            if (optionsButton != null) optionsButton.setVisibility(View.GONE);
            if (statisticsButton != null) statisticsButton.setVisibility(View.GONE);
            // Show forfeit and countdown controls
            if (forfeitButton != null) {
                forfeitButton.setVisibility(View.VISIBLE);
                // Move forfeitButton to statisticsButton's position if both exist
                if (statisticsButton != null) {
                    forfeitButton.setX(statisticsButton.getX());
                    forfeitButton.setY(statisticsButton.getY());
                }
            }
            if (countdownTextView != null) {
                countdownTextView.setVisibility(View.VISIBLE);
                // Move countdownTextView to optionsButton's position if both exist
                if (optionsButton != null) {
                    countdownTextView.setX(optionsButton.getX());
                    countdownTextView.setY(optionsButton.getY());
                }
            }
        } else {
            // Single-player: show options and statistics, hide forfeit and countdown
            if (optionsButton != null) optionsButton.setVisibility(View.VISIBLE);
            if (statisticsButton != null) statisticsButton.setVisibility(View.VISIBLE);
            if (forfeitButton != null) forfeitButton.setVisibility(View.GONE);
            if (countdownTextView != null) countdownTextView.setVisibility(View.GONE);
        }

        // ... add your game logic here ...
    }

    private void startGameCountdown() {
        if (CDHasStarted) {
            return;
        }
        CDHasStarted = true;
        if (gameCountDownTimer != null) {
            gameCountDownTimer.cancel();
        }
        if (isMultiplayer) {
            long timerDuration = GAME_DURATION_MS;
            Log.d(TAG, "startGameCountdown: starting multiplayer timer durationMs=" + timerDuration);
            if (countdownTextView != null) countdownTextView.setVisibility(View.VISIBLE);
            int minutes = (int) (timerDuration / 60000);
            int seconds = (int) ((timerDuration / 1000) % 60);
            String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            if (countdownTextView != null) countdownTextView.setText(timeFormatted);
            gameCountDownTimer = new CountDownTimer(timerDuration, 1000) {
                public void onTick(long millisUntilFinished) {
                    int minutes = (int) (millisUntilFinished / 60000);
                    int seconds = (int) ((millisUntilFinished / 1000) % 60);
                    String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
                    countdownTextView.setText(timeFormatted);
                }
                public void onFinish() {
                    countdownTextView.setText("00:00");
                    Log.d(TAG, "startGameCountdown: timer finished -> timeout verdict");
                    endGameWithVerdict("timeout");
                }
            }.start();
        } else {
            if (countdownTextView != null) countdownTextView.setVisibility(View.GONE);
            gameCountDownTimer = null;
        }
    }



    private void addLetter(char letter) {
        if (currentGuess.length() < 5) {
            currentGuess.append(letter);
            updateCurrentGuessUI();
        }
    }

    private void removeLetter() {
        if (currentGuess.length() > 0) {
            currentGuess.deleteCharAt(currentGuess.length() - 1);
            updateCurrentGuessUI();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isMultiplayer) {
            Log.d(TAG, "onStart: setting up duel listener for " + duelId);
            setupDuelListener();
        }
    }

    @Override
    protected void onStop() {
        // Listener removal moved to onDestroy to ensure timer stops for both players when game ends
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (isMultiplayer && !isFinishing() && !hasSubmittedResult) {
            Log.d(TAG, "onDestroy: app killed without verdict -> auto forfeit");
            endGameWithVerdict("forfeit");
        }
        if (isMultiplayer) {
            Log.d(TAG, "onDestroy: clearing inGame=false and cleaning up listeners/timers for " + myUid);
            db.collection("users").document(myUid).update("inGame", false);
            if (duelListener != null) {
                duelListener.remove();
            }
            if (gameCountDownTimer != null) {
                gameCountDownTimer.cancel();
            }
        }
        super.onDestroy();
    }

    // Multiplayer: End game with a verdict and update Firestore
    private void endGameWithVerdict(String verdict) {
        Log.d(TAG, "endGameWithVerdict: verdict=" + verdict + " hasSubmittedResult=" + hasSubmittedResult);
        if ("forfeit".equals(verdict)) {
            hasSubmittedResult = true;
            myFinalVerdict = verdict;
            if (gameCountDownTimer != null) {
                gameCountDownTimer.cancel();
            }
            Map<String, Object> myResult = new HashMap<>();
            myResult.put("verdict", "forfeit");
            Map<String, Object> oppResult = new HashMap<>();
            oppResult.put("verdict", "win");
            Map<String, Object> update = new HashMap<>();
            update.put(myUid, myResult);
            update.put(opponentUid, oppResult);
            db.collection("duels").document(duelId).set(update, com.google.firebase.firestore.SetOptions.merge());
            Log.d(TAG, "endGameWithVerdict: wrote forfeit result for duel=" + duelId);
            return;
        }
        if (hasSubmittedResult) {
            return;
        }
        hasSubmittedResult = true;
        myFinalVerdict = verdict;
        if (gameCountDownTimer != null) {
            gameCountDownTimer.cancel();
        }
        if (isMultiplayer) {
            showWaitingForOpponentDialog();
        }
        Map<String, Object> myResult = new HashMap<>();
        myResult.put("verdict", verdict);
        db.collection("duels").document(duelId).set(
                Collections.singletonMap(myUid, myResult),
                com.google.firebase.firestore.SetOptions.merge()
        );
        Log.d(TAG, "endGameWithVerdict: wrote my verdict=" + verdict + " for duel=" + duelId);
    }


    // Multiplayer: Compute duel verdict
    private String computeDuelVerdict(Map<String, Object> myData, Map<String, Object> oppData) {
        String myVerdict = myData.get("verdict") != null ? myData.get("verdict").toString() : "";
        String oppVerdict = oppData.get("verdict") != null ? oppData.get("verdict").toString() : "";
        String answerInfo = (targetWord != null) ? "\n\nAnswer: " + targetWord.toUpperCase() : "";
        if (myVerdict.equals("forfeit") && oppVerdict.equals("forfeit")) {
            return "Draw! Both forfeited." + answerInfo;
        } else if (myVerdict.equals("forfeit")) {
            return "You lose! (You forfeited)" + answerInfo;
        } else if (oppVerdict.equals("forfeit")) {
            return "You win! (Opponent forfeited)" + answerInfo;
        }
        if (myVerdict.equals("win") && oppVerdict.equals("win")) {
            return "Draw! Both guessed the word at the same time." + answerInfo;
        } else if (myVerdict.equals("win")) {
            return "You win!" + answerInfo;
        } else if (oppVerdict.equals("win")) {
            return "You lose! Opponent guessed the word." + answerInfo;
        }
        if ((myVerdict.equals("lose") || myVerdict.equals("timeout")) &&
                (oppVerdict.equals("lose") || oppVerdict.equals("timeout"))) {
            return "Draw! Both failed to guess the word." + answerInfo;
        }
        return "Game ended." + answerInfo;
    }

    // Multiplayer: Show duel verdict dialog
    private void showDuelVerdictDialog(String verdictMessage) {
        runOnUiThread(() -> {
            // Stop the countdown timer when the game is over in multiplayer
            if (isMultiplayer && gameCountDownTimer != null) {
                gameCountDownTimer.cancel();
            }
            Log.d(TAG, "showDuelVerdictDialog: showing final verdict and finishing duel " + duelId);
            new AlertDialog.Builder(this)
                    .setTitle("Game Over")
                    .setMessage(verdictMessage)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        resetDuelDocument(); // Reset after match ends
                        finish();
                    })
                    .show();
        });
    }

    // Multiplayer: Fetch or set target word for the duel
    private void fetchOrSetTargetWord() {
        db.collection("duels").document(duelId).get().addOnSuccessListener(snapshot -> {
            String tw = null;
            if (snapshot.exists() && snapshot.contains("targetWord")) {
                tw = snapshot.getString("targetWord");
            }
            if (tw == null) {
                // Determine host by player1 field in duel document
                String player1Uid = snapshot.getString("player1");
                Log.d(TAG, "fetchOrSetTargetWord: host player1=" + player1Uid + " myUid=" + myUid);
                if (player1Uid != null && player1Uid.equals(myUid)) {
                    // This client is the host, set the target word
                    List<String> wordList = standardWordList;
                    if (wordList.isEmpty()) {
                        throw new IllegalStateException("Word list is empty!");
                    }
                    Random random = new Random();
                    tw = wordList.get(random.nextInt(wordList.size()));
                    String finalTargetWord = tw;
                    db.collection("duels").document(duelId)
                            .update("targetWord", tw)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "fetchOrSetTargetWord: set targetWord as host=" + finalTargetWord);
                                startNewGameWithTarget(finalTargetWord);
                            })
                            .addOnFailureListener(e -> {
                                db.collection("duels").document(duelId)
                                        .set(Collections.singletonMap("targetWord", finalTargetWord), com.google.firebase.firestore.SetOptions.merge())
                                        .addOnSuccessListener(unused2 -> {
                                            Log.d(TAG, "fetchOrSetTargetWord: set targetWord via merge as host=" + finalTargetWord);
                                            startNewGameWithTarget(finalTargetWord);
                                        });
                            });
                } else {
                    // Not the host, listen for targetWord to be set
                    db.collection("duels").document(duelId)
                            .addSnapshotListener((doc, e) -> {
                                if (e != null || doc == null || !doc.exists()) return;
                                String tw2 = doc.getString("targetWord");
                                if (tw2 != null) {
                                    Log.d(TAG, "fetchOrSetTargetWord: received targetWord as guest=" + tw2);
                                    startNewGameWithTarget(tw2);
                                }
                            });
                }
            } else {
                Log.d(TAG, "fetchOrSetTargetWord: using existing targetWord=" + tw);
                startNewGameWithTarget(tw);
            }
            targetWord = tw;
        });
    }

    // Multiplayer: Start new game with a specific target word
    private void startNewGameWithTarget(String tw) {
        targetWord = tw;
        gameState = new WordleGameState(targetWord);
        Log.d(TAG, "startNewGameWithTarget: targetWord=" + tw + " starting countdown");
        // ...reset UI and state as needed...
        if (isMultiplayer) {
            startGameCountdown();
        }
    }

    // Multiplayer: Setup Firestore listener for duel verdicts and game start
    private void setupDuelListener() {
        if (duelListener != null) duelListener.remove();
        Log.d(TAG, "setupDuelListener: attaching duel listener for " + duelId);
        duelListener = db.collection("duels").document(duelId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    Map<String, Object> data = (Map<String, Object>) snapshot.getData();
                    if (data == null) return;
                    Map<String, Object> myData = (Map<String, Object>) data.get(myUid);
                    Map<String, Object> oppData = (Map<String, Object>) data.get(opponentUid);
                    String myVerdict = myData != null && myData.get("verdict") != null ? myData.get("verdict").toString() : null;
                    String oppVerdict = oppData != null && oppData.get("verdict") != null ? oppData.get("verdict").toString() : null;
                    Log.d(TAG, "setupDuelListener: snapshot verdicts my=" + myVerdict + " opp=" + oppVerdict);
                    boolean oppGuessed = "win".equals(oppVerdict);
                    // Do NOT cancel timer here; let each user's timer run independently
                    // if ((myVerdict != null || oppVerdict != null) && isMultiplayer && gameCountDownTimer != null) {
                    //     gameCountDownTimer.cancel();
                    // }
                    if (oppGuessed && !hasSubmittedResult) {
                        Log.d(TAG, "setupDuelListener: opponent won -> submitting lose");
                        endGameWithVerdict("lose");
                    }
                    if (myVerdict != null && oppVerdict != null) {
                        dismissWaitingForOpponentDialog();
                        showDuelVerdictDialog(computeDuelVerdict(myData, oppData));
                    }
                });
    }

    private void showGameEndDialog(boolean won) {
        String answer = gameState.getTargetWord().toUpperCase();
        String message = won ?
                ("Congratulations! You guessed the word!\n\nAnswer: " + answer) :
                ("Do better next time!\n\nAnswer: " + answer);
        updateStatistics(won);
        int attempts = gameState.getGuesses().size();
        String wordKey = gameState.getTargetWord().toLowerCase();
        int userBar = won ? attempts : 7; // 1-6 for win, 7 for fail
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String statsCol = "word_stats";
        String statsDoc = wordKey;
        String field = won ? ("attempt_" + attempts) : "fail";
        // Increment the relevant field atomically, then fetch and show dialog only after update
        firestore.collection(statsCol).document(statsDoc)
                .update(field, com.google.firebase.firestore.FieldValue.increment(1))
                .addOnSuccessListener(unused -> fetchAndShowStatsDialog(firestore, statsCol, statsDoc, userBar, message, won))
                .addOnFailureListener(e -> {
                    // If doc doesn't exist, create it, then fetch and show dialog
                    Map<String, Object> init = new HashMap<>();
                    for (int i = 1; i <= 6; i++) init.put("attempt_" + i, 0);
                    init.put("fail", 0);
                    init.put(field, 1);
                    firestore.collection(statsCol).document(statsDoc).set(init)
                            .addOnSuccessListener(unused -> fetchAndShowStatsDialog(firestore, statsCol, statsDoc, userBar, message, won));
                });
    }

    private void fetchAndShowStatsDialog(FirebaseFirestore firestore, String statsCol, String statsDoc, int userBar, String message, boolean won) {
        firestore.collection(statsCol).document(statsDoc).get().addOnSuccessListener(snapshot -> {
            Map<String, Object> stats = snapshot.getData();
            if (stats == null) {
                stats = new HashMap<>();
                for (int i = 1; i <= 6; i++) stats.put("attempt_" + i, 0L);
                stats.put("fail", 0L);
            }
            // Inflate bar chart layout using BarChartView
            LayoutInflater inflater = getLayoutInflater();
            View barChartLayout = inflater.inflate(R.layout.bar_chart_stats_dialog, null);
            com.example.wordle.BarChartView barChartView = barChartLayout.findViewById(R.id.barChartViewDialog);
            // Prepare values for BarChartView (first 6 attempts + fail)
            int[] barValues = new int[7];
            for (int i = 0; i < 6; i++) {
                barValues[i] = ((Number)stats.getOrDefault("attempt_" + (i+1), 0)).intValue();
            }
            barValues[6] = ((Number)stats.getOrDefault("fail", 0)).intValue(); // Add fail count
            barChartView.setValues(barValues);
            // Show dialog with message and bar chart
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(won ? "You Win!" : "Game Over");
            builder.setMessage(message);
            builder.setView(barChartLayout);
            builder.setCancelable(false);
            builder.setPositiveButton("Restart", (dialog, which) -> restartGame());
            builder.show();
        });
    }

    private void updateStatistics(boolean won) {
        SharedPreferences prefs = getSharedPreferences("wordle_stats", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        int totalGames = prefs.getInt("total_games", 0) + 1;
        int wins = prefs.getInt("wins", 0);
        int losses = prefs.getInt("losses", 0);
        if (won) {
            wins++;
            int attempts = gameState.getGuesses().size();
            if (attempts >= 1 && attempts <= 6) {
                String key = "solved_in_" + attempts;
                int solved = prefs.getInt(key, 0) + 1;
                editor.putInt(key, solved);
            }
        } else {
            losses++;
        }
        editor.putInt("total_games", totalGames);
        editor.putInt("wins", wins);
        editor.putInt("losses", losses);
        editor.apply();
    }

    private void loadWordLists() {
        // Load standard word list
        try (InputStream is = getAssets().open("WordleStandard.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim().toLowerCase());
            }
            String allWords = sb.toString();
            for (int i = 0; i + 5 <= allWords.length(); i += 5) {
                standardWordList.add(allWords.substring(i, i + 5));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Load all words list
        try (InputStream is = getAssets().open("Wordle.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim().toLowerCase());
            }
            String allWords = sb.toString();
            for (int i = 0; i + 5 <= allWords.length(); i += 5) {
                String word = allWords.substring(i, i + 5);
                allWordList.add(word);   // Used for picking a random word
                allowedWords.add(word);  // Used for checking if a guess is valid
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startNewGame() {
        List<String> wordList = includeAllWords ? allWordList : standardWordList;
        if (wordList.isEmpty()) {
            throw new IllegalStateException("Word list is empty!");
        }
        Random random = new Random();
        targetWord = wordList.get(random.nextInt(wordList.size()));
        gameState = new WordleGameState(targetWord);
        // ...reset UI and state as needed...
        startGameCountdown(); // Always reset timer to 5:00 when starting a new game
    }

    private void restartGame() {
        CDHasStarted = false;
        if (isMultiplayer) {
            fetchOrSetTargetWord();
        } else {
            startNewGame();
        }
        // ...reset UI and state as needed...
        recreate(); // Optionally recreate the activity for a full reset
    }

    private boolean isGuessValidForHardMode(String guess) {
        // If not in hard mode, always valid
        if (!hardMode) return true;
        List<String> guesses = gameState.getGuesses();
        if (guesses.isEmpty()) return true;
        String target = gameState.getTargetWord();
        // Track known green and yellow letters
        Map<Integer, Character> greenPositions = new HashMap<>();
        Set<Character> yellowLetters = new HashSet<>();
        // Efficient: Only track counts for previous guess
        Map<Character, Integer> prevGreenYellowCounts = new HashMap<>();
        if (!guesses.isEmpty()) {
            String prevGuess = guesses.get(guesses.size() - 1);
            boolean[] targetUsed = new boolean[5];
            // First pass: green
            for (int col = 0; col < 5; col++) {
                char g = prevGuess.charAt(col);
                if (g == target.charAt(col)) {
                    greenPositions.put(col, g);
                    targetUsed[col] = true;
                    prevGreenYellowCounts.put(g, prevGreenYellowCounts.getOrDefault(g, 0) + 1);
                }
            }
            // Second pass: yellow
            for (int col = 0; col < 5; col++) {
                char g = prevGuess.charAt(col);
                if (g != target.charAt(col)) {
                    for (int t = 0; t < 5; t++) {
                        if (!targetUsed[t] && g == target.charAt(t)) {
                            yellowLetters.add(g);
                            targetUsed[t] = true;
                            prevGreenYellowCounts.put(g, prevGreenYellowCounts.getOrDefault(g, 0) + 1);
                            break;
                        }
                    }
                }
            }
        }
        // Check green positions
        for (Map.Entry<Integer, Character> entry : greenPositions.entrySet()) {
            if (guess.charAt(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        // Check yellow letters
        for (char yellow : yellowLetters) {
            if (!guess.contains(String.valueOf(yellow))) {
                return false;
            }
        }
        // Efficient: Check counts for previous guess's green/yellow letters
        if (!prevGreenYellowCounts.isEmpty()) {
            Map<Character, Integer> guessCounts = new HashMap<>();
            for (char c : guess.toCharArray()) {
                guessCounts.put(c, guessCounts.getOrDefault(c, 0) + 1);
            }
            for (Map.Entry<Character, Integer> entry : prevGreenYellowCounts.entrySet()) {
                if (guessCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }
    private void showDismissableToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        currentToast.show();
    }

    private void submitGuess() {
        if (currentGuess.length() == 5) {
            String guess = currentGuess.toString().toLowerCase();
            if (!allowedWords.contains(guess)) {
                showDismissableToast("Not in word list!");
                return;
            }
            // Hard mode validation
            if (!isGuessValidForHardMode(guess)) {
                showDismissableToast("Hard mode: Use all yellow letters and keep green letters in place!");
                return;
            }
            gameState.addGuess(guess);
            currentGuess.setLength(0);
            updateGuessesUI();
            updateCurrentGuessUI();
            // Check for win/game over here
            List<String> guesses = gameState.getGuesses();
            String target = gameState.getTargetWord();
            boolean won = guesses.get(guesses.size() - 1).equalsIgnoreCase(target);
            if (won || guesses.size() == 6) {
                if (isMultiplayer) {
                    endGameWithVerdict(won ? "win" : "lose");
                } else {
                    showGameEndDialog(won);
                }
            }
        }
    }

    // Dummy UI update methods (implement these to update your TextViews, etc.)x
    private void updateCurrentGuessUI() {
        int guessIndex = gameState.getGuesses().size();
        for (int i = 0; i < 5; i++) {
            int resId = getResources().getIdentifier("cell_" + guessIndex + "_" + i, "id", getPackageName());
            TextView tv = findViewById(resId);
            if (tv != null) {
                if (i < currentGuess.length()) {
                    tv.setText(String.valueOf(currentGuess.charAt(i)));
                } else {
                    tv.setText("");
                }
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            }
        }
    }


    private void updateGuessesUI() {
        List<String> guesses = gameState.getGuesses();
        String target = gameState.getTargetWord();
        keyColors.clear();

        for (int row = 0; row < guesses.size(); row++) {
            String guess = guesses.get(row);
            boolean[] targetUsed = new boolean[5];
            // First pass: mark correct positions (green)
            for (int col = 0; col < 5; col++) {
                int resId = getResources().getIdentifier("cell_" + row + "_" + col, "id", getPackageName());
                TextView tv = findViewById(resId);
                char g = Character.toUpperCase(guess.charAt(col));
                tv.setText(String.valueOf(g));
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                tv.setBackgroundResource(R.drawable.cell_border);
                if (g == Character.toUpperCase(target.charAt(col))) {
                    ViewCompat.setBackgroundTintList(tv, android.content.res.ColorStateList.valueOf(0xFF66BB6A)); // green
                    targetUsed[col] = true;
                    keyColors.put(g, 2); // 2 = green
                } else {
                    ViewCompat.setBackgroundTintList(tv, android.content.res.ColorStateList.valueOf(0xFF8C8C8C)); // gray
                    if (!keyColors.containsKey(g)) keyColors.put(g, 0); // 0 = gray
                }
            }
            // Second pass: mark present letters (yellow)
            for (int col2 = 0; col2 < 5; col2++) {
                char g2 = Character.toUpperCase(guess.charAt(col2));
                if (g2 != Character.toUpperCase(target.charAt(col2))) {
                    for (int t = 0; t < 5; t++) {
                        if (!targetUsed[t] && g2 == Character.toUpperCase(target.charAt(t))) {
                            int resId2 = getResources().getIdentifier("cell_" + row + "_" + col2, "id", getPackageName());
                            TextView tv2 = findViewById(resId2);
                            tv2.setBackgroundResource(R.drawable.cell_border);
                            ViewCompat.setBackgroundTintList(tv2, android.content.res.ColorStateList.valueOf(0xFFFFEB3B)); // yellow
                            targetUsed[t] = true;
                            if (keyColors.getOrDefault(g2, 0) < 1) keyColors.put(g2, 1); // 1 = yellow
                            break;
                        }
                    }
                }
            }
        }
        updateKeyboardColors();
    }

    private void updateKeyboardColors() {
        for (char c = 'A'; c <= 'Z'; c++) {
            int resId = getResources().getIdentifier("key_" + Character.toLowerCase(c), "id", getPackageName());
            View key = findViewById(resId);
            if (key != null) {
                // Use ripple background for keys
                key.setBackgroundResource(R.drawable.rounded_button_ripple);
                Integer status = keyColors.getOrDefault(c, -1);
                if (status == 2) {
                    ViewCompat.setBackgroundTintList(key, android.content.res.ColorStateList.valueOf(0xFF66BB6A)); // green
                } else if (status == 1) {
                    ViewCompat.setBackgroundTintList(key, android.content.res.ColorStateList.valueOf(0xFFFFEB3B)); // yellow
                } else if (status == 0) {
                    ViewCompat.setBackgroundTintList(key, android.content.res.ColorStateList.valueOf(0xFF8C8C8C)); // gray
                } else {
                    ViewCompat.setBackgroundTintList(key, null); // default, use selector
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void endMultiplayerGameAsLoser() {
        // No-op: multiplayer logic removed
    }

    private void cleanupSession() {
        // No-op: multiplayer logic removed
    }

    // Multiplayer: Reset duel document
    private void resetDuelDocument() {
        if (db != null && duelId != null) {
            Map<String, Object> reset = new HashMap<>();
            reset.put("player1", myUid);
            reset.put("player2", opponentUid);
            reset.put("3_sec_countdown_start_time", null);
            reset.put("targetWord", null);
            reset.put(myUid, null);
            reset.put(opponentUid, null);
            db.collection("duels").document(duelId).set(reset);
        }
    }

    private void showWaitingForOpponentDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Waiting for Opponent");
        builder.setMessage("Waiting for the other player to finish...");
        builder.setCancelable(true); // Allow interaction with other UI elements
        waitingDialog = builder.create();
        waitingDialog.show();
    }

    private void dismissWaitingForOpponentDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        if (isMultiplayer) {
            // Show forfeit dialog instead of allowing back navigation
            new AlertDialog.Builder(this)
                    .setTitle("Forfeit Match")
                    .setMessage("You must forfeit to exit the game.")
                    .setPositiveButton("Forfeit", (dialog, which) -> endGameWithVerdict("forfeit"))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        // No custom logic needed; default behavior is fine
        super.onUserLeaveHint();
    }

    // When hint button is clicked in options dialog:
    private void onHintButtonClicked() {
        if (isMultiplayer) {
            Toast.makeText(this, "Hints not available in multiplayer", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetWord == null || targetWord.isEmpty()) {
            Toast.makeText(this, "Game not initialized. Please try again.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onHintButtonClicked: targetWord is null or empty");
            return;
        }

        if (cachedHint != null) {
            // Already fetched, show cached hint
            showHintDialog(cachedHint);
        } else {
            // Fetch from Gemini API
            Log.d(TAG, "onHintButtonClicked: fetching hint for word: " + targetWord);
            fetchHintFromGemini(targetWord);
        }
    }

    private void fetchHintFromGemini(String word) {
        String apiKey = getString(R.string.gemini_api_key);

        // Build the prompt
        String promptText = "Provide a short hint about the MEANING of the word \"" + word + "\". " +
                "Do NOT mention letters, spelling, or structure. " +
                "Keep it to 1-2 short sentences. " +
                "Examples:\n" +
                "- You might feel this after achieving something difficult\n" +
                "- A sudden realization that changes how you see things\n" +
                "- Used to describe something that brings calm or comfort";

        // Call Gemini API on background thread using REST API with retry logic
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // Try gemini-2.5-flash-lite first, then fallback to gemini-1.5-flash
            String[] models = {"gemini-2.5-flash-lite", "gemini-1.5-flash"};
            boolean success = false;
            
            for (String model : models) {
                if (success) break;
                
                // Retry up to 3 times with exponential backoff
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        String urlString = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                        Log.d(TAG, "Attempting hint fetch with model=" + model + ", attempt=" + (attempt + 1));
                        
                        URL url = new URL(urlString);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);

                        // Build JSON request body
                        JSONObject requestBody = new JSONObject();
                        JSONArray contentsArray = new JSONArray();
                        JSONObject contentObj = new JSONObject();
                        JSONArray partsArray = new JSONArray();
                        JSONObject partObj = new JSONObject();
                        partObj.put("text", promptText);
                        partsArray.put(partObj);
                        contentObj.put("parts", partsArray);
                        contentsArray.put(contentObj);
                        requestBody.put("contents", contentsArray);

                        // Send request
                        OutputStream os = conn.getOutputStream();
                        os.write(requestBody.toString().getBytes("UTF-8"));
                        os.close();

                        // Read response
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();

                            // Parse response JSON
                            JSONObject responseJson = new JSONObject(response.toString());
                            String hint = responseJson
                                    .getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text")
                                    .trim();

                            Log.d(TAG, "Successfully fetched hint with model=" + model);
                            runOnUiThread(() -> {
                                cachedHint = hint;
                                showHintDialog(hint);
                            });
                            success = true;
                            conn.disconnect();
                            break; // Exit retry loop
                        } else if (responseCode == 503) {
                            // Model overloaded - read error and retry
                            String errorBody = "";
                            try {
                                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                                StringBuilder errorResponse = new StringBuilder();
                                String line;
                                while ((line = errorReader.readLine()) != null) {
                                    errorResponse.append(line);
                                }
                                errorReader.close();
                                errorBody = errorResponse.toString();
                            } catch (Exception e) {
                                errorBody = "Unable to read error response";
                            }
                            
                            Log.w(TAG, "Model " + model + " overloaded (503), attempt " + (attempt + 1) + "/3: " + errorBody);
                            conn.disconnect();
                            
                            // Wait before retry (exponential backoff: 1s, 2s, 4s)
                            if (attempt < 2) {
                                Thread.sleep((long) Math.pow(2, attempt) * 1000);
                            }
                        } else {
                            // Other error - read and log, then try next model
                            String errorBody = "";
                            try {
                                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                                StringBuilder errorResponse = new StringBuilder();
                                String line;
                                while ((line = errorReader.readLine()) != null) {
                                    errorResponse.append(line);
                                }
                                errorReader.close();
                                errorBody = errorResponse.toString();
                            } catch (Exception e) {
                                errorBody = "Unable to read error response";
                            }
                            
                            Log.e(TAG, "Model " + model + " error HTTP " + responseCode + ": " + errorBody);
                            conn.disconnect();
                            break; // Try next model
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception with model " + model + ", attempt " + (attempt + 1), e);
                        if (attempt == 2) break; // Last attempt, try next model
                        try {
                            Thread.sleep((long) Math.pow(2, attempt) * 1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (!success) {
                runOnUiThread(() -> {
                    Toast.makeText(GameActivity.this, "Hint service temporarily unavailable. Please try again.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "All hint fetch attempts failed");
                });
            }
        });
    }

    private void showHintDialog(String hint) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Hint")
                .setMessage(hint)
                .setCancelable(true)
                .create();
        dialog.show();
        // Tapping outside dismisses automatically (default behavior)
    }
}


