package com.example.wordle;


import java.util.ArrayList;
import java.util.List;

public class WordleGameState {
    private static final int MAX_GUESSES = 6;
    private static final int WORD_LENGTH = 5;
    private String targetWord;
    private List<String> guesses;

    public WordleGameState(String targetWord) {
        this.targetWord = targetWord;
        this.guesses = new ArrayList<>();
    }

    public String getTargetWord() { return targetWord; }
    public List<String> getGuesses() { return guesses; }

    public void addGuess(String guess) {
        if (guess.length() == WORD_LENGTH && guesses.size() < MAX_GUESSES) {
            guesses.add(guess);
        }
    }
}
