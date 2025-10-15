package com.example.wordle;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Random;

public class MultiplayerActivity extends AppCompatActivity {

    private static final String TAG = "MultiplayerActivity";
    private static final String PREFS_NAME = "WordlePrefs";
    private static final String KEY_UID = "user_uid";
    private static final int UID_LENGTH = 6;

    private TextView textViewMyUid;
    private EditText editTextOpponentUid;
    private Button buttonPlayMultiplayer;
    private FirebaseFirestore db;
    private boolean isReady = false;
    private boolean isMatchmaking = false;
    private ListenerRegistration myListenerRegistration;
    private ListenerRegistration opponentListenerRegistration;
    private ListenerRegistration duelListenerRegistration;
    private String myUid;
    private String opponentUid;

    private long countdownStartTime = 0L;
    private android.os.CountDownTimer countdownTimer;
    private static final int COUNTDOWN_SECONDS = 3;
    private boolean isCooldownActive = false;
    private Long lastCountdownStartTime = null; // Track last countdown start time

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer);

        textViewMyUid = findViewById(R.id.textViewMyUid);
        editTextOpponentUid = findViewById(R.id.editTextOpponentUid);
        buttonPlayMultiplayer = findViewById(R.id.buttonPlayMultiplayer);

        // Limit input to 6 characters
        editTextOpponentUid.setFilters(new InputFilter[]{new InputFilter.LengthFilter(UID_LENGTH)});

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uid = prefs.getString(KEY_UID, null);
        if (uid != null) {
            myUid = uid;
            initializeMultiplayerUI(myUid);
        } else {
            generateAndStoreUid(newUid -> {
                myUid = newUid;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_UID, newUid);
                editor.apply();
                initializeMultiplayerUI(myUid);
            });
        }
    }

    // Helper to generate a unique UID and ensure Firestore uniqueness
    private void generateAndStoreUid(final java.util.function.Consumer<String> callback) {
        generateUniqueUidTransaction(callback, 0);
    }

    // UI and Firestore setup logic, called after UID is determined
    private void initializeMultiplayerUI(String uid) {
        textViewMyUid.setText(uid);
        // Create or update user document in Firestore (without setting ready=true)
        java.util.HashMap<String, Object> userData = new java.util.HashMap<>();
        userData.put("uid", uid);
        userData.put("opponent_uid", "");
        userData.put("ready", false);
        userData.put("inGame", false);
        db.collection("users").document(uid).set(userData, SetOptions.merge());

        // Enable Play button only if entered UID is 6 chars and not same as own UID
        editTextOpponentUid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String enteredUid = s.toString().trim().toUpperCase();
                boolean validFormat = enteredUid.length() == UID_LENGTH && !enteredUid.equals(uid);
                if (validFormat) {
                    // Check Firestore for existence of enteredUid
                    db.collection("users").document(enteredUid).get().addOnSuccessListener(documentSnapshot -> {
                        boolean exists = documentSnapshot.exists();
                        // Only enable if not in cooldown
                        buttonPlayMultiplayer.setEnabled(exists && !isCooldownActive);
                        if (exists) {
                            if (!enteredUid.equals(opponentUid)) {
                                opponentUid = enteredUid;
                                attachFirestoreListeners();
                            }
                        } else {
                            detachFirestoreListeners();
                        }
                    });
                } else {
                    buttonPlayMultiplayer.setEnabled(false);
                    detachFirestoreListeners();
                }
            }
        });
        buttonPlayMultiplayer.setOnClickListener(v -> {
            // Directly update Firestore, since button is only enabled for valid/existing UIDs
            isReady = !isReady;
            java.util.HashMap<String, Object> updateData = new java.util.HashMap<>();
            updateData.put("opponent_uid", opponentUid);
            updateData.put("ready", isReady);
            db.collection("users").document(uid).set(updateData, SetOptions.merge());
            if (isReady) {
                buttonPlayMultiplayer.setText(getString(R.string.cancel));
                buttonPlayMultiplayer.setBackgroundColor(ContextCompat.getColor(MultiplayerActivity.this, android.R.color.holo_red_dark));
                editTextOpponentUid.setEnabled(false); // Make opponent UID box non-editable when readied up
            } else {
                buttonPlayMultiplayer.setText(getString(R.string.play));
                buttonPlayMultiplayer.setBackgroundColor(ContextCompat.getColor(MultiplayerActivity.this, android.R.color.holo_blue_light));
                editTextOpponentUid.setEnabled(true); // Make opponent UID box editable when not readied up
            }
            // 1 second cooldown: disable button, then re-enable after 1 second
            isCooldownActive = true;
            buttonPlayMultiplayer.setEnabled(false);
            buttonPlayMultiplayer.postDelayed(() -> {
                isCooldownActive = false;
                buttonPlayMultiplayer.setEnabled(true);
                // Disable EditText if either isReady or isMatchmaking
                if (isReady || isMatchmaking) {
                    editTextOpponentUid.setEnabled(false);
                } else {
                    editTextOpponentUid.setEnabled(true);
                }
            }, 1000);
        });
        // Home button logic
        findViewById(R.id.buttonGoHome).setOnClickListener(v -> finish());
        buttonPlayMultiplayer.setEnabled(false); // Always start disabled
    }

    // Helper to generate a unique UID
    private void generateUniqueUidTransaction(final java.util.function.Consumer<String> callback, int attempt) {
        if (attempt > 10) {
            Toast.makeText(this, "Failed to generate unique UID. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }
        String newUid = generateRandomUid();
        DocumentReference userRef = db.collection("users").document(newUid);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                // UID is unique, store it and callback
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_UID, newUid);
                editor.apply();
                callback.accept(newUid);
            } else {
                // Collision, try again
                generateUniqueUidTransaction(callback, attempt + 1);
            }
        }).addOnFailureListener(e -> {
            // Try again on failure
            generateUniqueUidTransaction(callback, attempt + 1);
        });
    }

    // Helper to generate a random UID of length UID_LENGTH (A-Z, 0-9)
    private String generateRandomUid() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(UID_LENGTH);
        for (int i = 0; i < UID_LENGTH; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void attachFirestoreListeners() {
        detachFirestoreListeners(); // Remove any existing listeners first
        if (myUid == null || opponentUid == null) return;
        DocumentReference myDocRef = db.collection("users").document(myUid);
        DocumentReference opponentDocRef = db.collection("users").document(opponentUid);
        myListenerRegistration = myDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) return;
            if (snapshot != null && snapshot.exists()) {
                checkMatchmakingCondition(snapshot, null);
            }
        });
        opponentListenerRegistration = opponentDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) return;
            if (snapshot != null && snapshot.exists()) {
                checkMatchmakingCondition(null, snapshot);
            }
        });
        // Listen for 3_sec_countdown_start_time in duel document
        String duelId = getDuelId();
        if (duelId != null) {
            DocumentReference duelDocRef = db.collection("duels").document(duelId);
            duelListenerRegistration = duelDocRef.addSnapshotListener((snapshot, e) -> {
                Log.d(TAG, "Recognized a change in duel document");
                if (e != null) return;
                if (snapshot != null && snapshot.exists()) {
                    Object countdownStartObj = snapshot.get("3_sec_countdown_start_time");
                    Long countdownStartTime = (Long)countdownStartObj;
                    if (countdownStartTime == null) {
                        cancelCountdown(); // Cancel countdown for both players if field is cleared
                        lastCountdownStartTime = null;
                        return;
                    }
                    // Fetch both user documents to check readiness and matching
                    db.collection("users").document(myUid).get().addOnSuccessListener(myDoc -> {
                        db.collection("users").document(opponentUid).get().addOnSuccessListener(oppDoc -> {
                            Boolean myReady = myDoc.getBoolean("ready");
                            Boolean oppReady = oppDoc.getBoolean("ready");
                            String myOppUid = myDoc.getString("opponent_uid");
                            String oppOppUid = oppDoc.getString("opponent_uid");
                            boolean bothReadyAndMatched = Boolean.TRUE.equals(myReady) && Boolean.TRUE.equals(oppReady)
                                    && myUid.equals(oppOppUid) && opponentUid.equals(myOppUid);
                            Log.d(TAG, "Countdown detected. bothReadyAndMatched=" + bothReadyAndMatched + ", countdownStartTime=" + countdownStartTime);
                            if (bothReadyAndMatched) {
                                if (!countdownStartTime.equals(lastCountdownStartTime)) {
                                    lastCountdownStartTime = countdownStartTime;
                                    startSynchronizedCountdown(countdownStartTime);
                                }
                            } else {
                                cancelCountdown();
                                lastCountdownStartTime = null;
                            }
                        });
                    });
                }
            });
        }
    }

    private void detachFirestoreListeners() {
        if (myListenerRegistration != null) {
            myListenerRegistration.remove();
            myListenerRegistration = null;
        }
        if (opponentListenerRegistration != null) {
            opponentListenerRegistration.remove();
            opponentListenerRegistration = null;
        }
        if (duelListenerRegistration != null) {
            duelListenerRegistration.remove();
            duelListenerRegistration = null;
        }
    }

    // In MultiplayerActivity.java

    private void checkMatchmakingCondition(@Nullable DocumentSnapshot mySnap, @Nullable DocumentSnapshot oppSnap) {
        // Always fetch both snapshots if either is missing
        if (mySnap == null || oppSnap == null) {
            db.collection("users").document(myUid).get().addOnSuccessListener(myDoc -> {
                db.collection("users").document(opponentUid).get().addOnSuccessListener(oppDoc -> {
                    checkMatchmakingCondition(myDoc, oppDoc);
                });
            });
            return;
        }
        Boolean myReady = mySnap.getBoolean("ready");
        Boolean oppReady = oppSnap.getBoolean("ready");
        String myOppUid = mySnap.getString("opponent_uid");
        String oppOppUid = oppSnap.getString("opponent_uid");

        // --- START of new code to add ---
        // Safely get the inGame status. Default to 'false' if the field doesn't exist.
        boolean myInGame = mySnap.getBoolean("inGame") != null && mySnap.getBoolean("inGame");
        boolean oppInGame = oppSnap.getBoolean("inGame") != null && oppSnap.getBoolean("inGame");
        // --- END of new code to add ---

        // Condition 1: Both players are ready and mutually matched.
        boolean bothReadyAndMatched = Boolean.TRUE.equals(myReady) && Boolean.TRUE.equals(oppReady)
                && myUid.equals(oppOppUid) && opponentUid.equals(myOppUid);

        // The final condition now includes checking if NEITHER player is in a game.
        if (bothReadyAndMatched && !myInGame && !oppInGame) {
            if (!isMatchmaking) {
                isMatchmaking = true;
                // Only one user should set the start_time to avoid race conditions
                String duelId = getDuelId();
                if (duelId != null) {
                    DocumentReference duelDocRef = db.collection("duels").document(duelId);
                    duelDocRef.get().addOnSuccessListener(duelSnap -> {
                        if (!duelSnap.exists()) {
                            // This client becomes host and creates the duel document with all required fields
                            java.util.HashMap<String, Object> duelData = new java.util.HashMap<>();
                            duelData.put("3_sec_countdown_start_time", null);
                            duelData.put("player1", myUid);
                            duelData.put("player2", opponentUid);
                            duelData.put("targetWord", null); // Target word will be set in GameActivity
                            duelData.put(myUid, null); // verdict/result for player1
                            duelData.put(opponentUid, null); // verdict/result for player2
                            duelDocRef.set(duelData);
                        }
                        Object countdownStartObj = duelSnap.get("3_sec_countdown_start_time");
                        String player1Uid = duelSnap.getString("player1");
                        Long countdownStartTime = null;
                        if (countdownStartObj instanceof Long) {
                            countdownStartTime = (Long) countdownStartObj;
                        }
                        // Only host (player1) sets the countdown
                        if (countdownStartTime == null && myUid.equals(player1Uid)) {
                            fetchServerTimestamp(serverTime -> {
                                long syncTime = serverTime + 500; // Add small buffer for sync
                                java.util.HashMap<String, Object> update = new java.util.HashMap<>();
                                update.put("3_sec_countdown_start_time", syncTime);
                                duelDocRef.set(update, SetOptions.merge());
                            });
                        }
                    });
                }
            }
            // The duelListener will handle starting the countdown when start_time is set
        } else {
            if (isMatchmaking) {
                isMatchmaking = false;
                // Cancel countdown and clear 3_sec_countdown_start_time from duel document
                cancelCountdown();
                String duelId = getDuelId();
                if (duelId != null) {
                    java.util.HashMap<String, Object> update = new java.util.HashMap<>();
                    update.put("3_sec_countdown_start_time", null);
                    db.collection("duels").document(duelId).set(update, SetOptions.merge());
                }
            }
        }
    }


    private String getDuelId() {
        // Generate a unique duelId by sorting both UIDs alphabetically and joining with an underscore
        if (myUid == null || opponentUid == null) return null;
        String[] uids = new String[]{myUid, opponentUid};
        java.util.Arrays.sort(uids);
        return uids[0] + "_" + uids[1];
    }

    private void startSynchronizedCountdown(long startTime) {
        Log.d(TAG, "Starting synchronized countdown" + startTime);
        cancelCountdown();
        countdownStartTime = startTime;
        fetchServerTimestamp(serverNow -> {
            long millisUntilStartRaw = startTime - serverNow;
            final long millisUntilStart = millisUntilStartRaw < 0 ? 0 : millisUntilStartRaw;
            long totalMillis = millisUntilStart + COUNTDOWN_SECONDS * 1000L;
            buttonPlayMultiplayer.setText(getString(R.string.starting_in, COUNTDOWN_SECONDS));
            countdownTimer = new android.os.CountDownTimer(totalMillis, 100) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long secondsLeft = (long)Math.ceil((millisUntilFinished - millisUntilStart) / 1000.0);
                    if (secondsLeft < 0) secondsLeft = 0;
                    buttonPlayMultiplayer.setText(getString(R.string.starting_in, secondsLeft));
                }
                @Override
                public void onFinish() {
                    buttonPlayMultiplayer.setText(getString(R.string.starting));
                    // Navigate to multiplayer game page
                    String duelId = getDuelId();
                    android.content.Intent intent = new android.content.Intent(MultiplayerActivity.this, GameActivity.class);
                    intent.putExtra("isMultiplayer", true);
                    intent.putExtra("myUid", myUid);
                    intent.putExtra("opponentUid", opponentUid);
                    intent.putExtra("duelId", duelId);
                    intent.putExtra("gameStartTime", countdownStartTime + COUNTDOWN_SECONDS * 1000L);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            };
            countdownTimer.start();
        });
    }

    private void cancelCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
            buttonPlayMultiplayer.setText(isReady ? getString(R.string.cancel) : getString(R.string.play));
        }
        // Always re-enable opponent UID box when countdown is cancelled
        if (!isReady && !isMatchmaking) {
            editTextOpponentUid.setEnabled(true);
        } else {
            editTextOpponentUid.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Set ready to false when leaving the activity
        if (myUid != null && db != null) {
            java.util.HashMap<String, Object> update = new java.util.HashMap<>();
            update.put("ready", false);
            db.collection("users").document(myUid).set(update, SetOptions.merge());
        }
        detachFirestoreListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-attach listeners if a valid opponentUid is present
        String enteredUid = editTextOpponentUid.getText().toString().trim().toUpperCase();
        boolean validFormat = enteredUid.length() == UID_LENGTH && !enteredUid.equals(myUid);
        if (validFormat) {
            // Check Firestore for existence of enteredUid (same as in TextWatcher)
            db.collection("users").document(enteredUid).get().addOnSuccessListener(documentSnapshot -> {
                boolean exists = documentSnapshot.exists();
                buttonPlayMultiplayer.setEnabled(exists);
                if (exists) {
                    opponentUid = enteredUid;
                    attachFirestoreListeners();
                } else {
                    detachFirestoreListeners();
                }
            });
        } else {
            buttonPlayMultiplayer.setEnabled(false);
            detachFirestoreListeners();
        }
        // Reset the Play/Cancel button and Firestore state after returning from game
        isReady = false;
        buttonPlayMultiplayer.setText(getString(R.string.play));
        buttonPlayMultiplayer.setBackgroundColor(ContextCompat.getColor(MultiplayerActivity.this, android.R.color.holo_blue_light));
        editTextOpponentUid.setEnabled(true); // Always re-enable opponent UID box on resume
        if (myUid != null && db != null) {
            java.util.HashMap<String, Object> update = new java.util.HashMap<>();
            update.put("ready", false);
            db.collection("users").document(myUid).set(update, SetOptions.merge());
        }
    }

    // Helper to fetch server timestamp from Firestore
    private void fetchServerTimestamp(java.util.function.Consumer<Long> callback) {
        DocumentReference tsRef = db.collection("server_time").document(myUid + "_ts");
        java.util.HashMap<String, Object> data = new java.util.HashMap<>();
        data.put("ts", FieldValue.serverTimestamp());
        tsRef.set(data).addOnSuccessListener(aVoid -> {
            tsRef.get().addOnSuccessListener(doc -> {
                Object tsObj = doc.get("ts");
                if (tsObj instanceof com.google.firebase.Timestamp) {
                    long serverMillis = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
                    callback.accept(serverMillis);
                } else {
                    // fallback: use local time if server time not available
                    callback.accept(System.currentTimeMillis());
                }
            });
        });
    }
}