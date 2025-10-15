# Multiplayer Matchmaking & Countdown

## Overview
This document summarizes the current implementation and logic for the multiplayer matchmaking and synchronized countdown features in the Wordle Android app. It is intended as a handoff for the next developer or AI agent.

---

## User Flow
1. **UIDs:** Each player sees their own unique 6-character UID.
2. **Opponent Entry:** Each player must enter the other player’s UID in the text box.
3. **Play Button:**
   - Disabled by default.
   - Enabled only when the entered UID is valid (6 characters, not the same as the user’s own UID).
4. **Readiness:**
   - Pressing Play sets `ready=true` and stores the opponent’s UID in Firestore; button turns red and says "Cancel".
   - Pressing Cancel sets `ready=false` and reverts the button.

## Firestore Structure
- **Collection:** `users`
- **Document:** Named by each user's UID
- **Fields:**
  - `uid`: the user’s own UID
  - `opponent_uid`: the entered opponent UID
  - `ready`: whether the user is ready
  - `start_time`: (added for countdown) the synchronized countdown start time in ms

## Matchmaking Condition
- Both users must:
  - Enter each other’s UID as `opponent_uid`
  - Have `ready=true`
- Only when both conditions are met for both users, the countdown to start the game begins.

## Countdown & Game Start
- When both players are ready, a `start_time` (server timestamp + small buffer) is written to both users’ documents.
- Both clients listen for this `start_time` and start a 3-second countdown, synchronized to the same moment.
- If either player cancels, `start_time` is cleared and the countdown is cancelled for both.
- After the countdown, the UI shows "Starting..." (navigation to the game page is a TODO).

## Implementation Status
- [x] Firestore listeners for both user and opponent documents
- [x] Matchmaking condition detection
- [x] Synchronized countdown logic using `start_time`
- [x] Countdown cancellation if either player cancels
- [ ] Navigation to the game page after countdown (TODO)
- [ ] UI/UX polish (e.g., string resources, color deprecation warnings)

## Known Issues & Notes
- If a user enters a valid but non-existent UID, matchmaking will never start (no error shown).
- The countdown is now synchronized and starts at 3 on both devices.
- Only one device sets `start_time`, but both listen for it and start the countdown.
- Edge cases (leaving the screen, rapid toggling) should be tested further.

## Next Steps / TODOs
- Implement navigation to the game page after the countdown.
- Improve user feedback for invalid or non-existent UIDs.
- Refactor UI code to use string resources and non-deprecated color methods.
- Consider moving matchmaking logic to a shared match document for scalability.

---

## Project Background (for Next AI Agent)

### App Overview
- Android Wordle app with multiplayer support, written in Java.
- Multiplayer logic uses Firebase Firestore for real-time synchronization between two players.
- Each multiplayer match (duel) is represented by a document in the `duels` Firestore collection.
- Each player's result (win/lose, verdict) is stored as a map under their UID in the duel document.

### Multiplayer Flow
- **Matchmaking:**
  - Each player enters the other's UID and sets `ready=true` in their user document.
  - When both are ready and have entered each other's UID, a synchronized countdown is started using a `start_time` field in both user documents.
  - After the countdown, both players are navigated to the game page (MultiplayerGameActivity).
- **Duel Document Creation:**
  - The duel document is created in MultiplayerGameActivity, not during matchmaking.
  - The document is named by a unique `duelId` (usually a combination of both UIDs, sorted alphabetically).
  - Initial fields: `player1`, `player2`, `createdAt`.
  - The target word is generated and stored in the duel document if it does not already exist.
- **Result Storage:**
  - When a player finishes, their result (map with `win` and `verdict`) is written under their UID in the duel document.
  - The app listens for both players' results to determine and display the final outcome (win, lose, draw, forfeit, timeout, etc.).

### Key Implementation Points
- Always pass `duelId` in the Intent when starting MultiplayerGameActivity.
- The duel document is only created/initialized in MultiplayerGameActivity.
- The target word is generated once and reused for both players.
- The endGameWithVerdict method writes the player's result and listens for both results to show the verdict.
- The Firestore structure for a duel document after both players finish:
  ```json
  {
    "player1": "UID1",
    "player2": "UID2",
    "createdAt": ...,
    "targetWord": "...",
    "UID1": { "win": true, "verdict": "win" },
    "UID2": { "win": false, "verdict": "lose" }
  }
  ```

### Next Steps
- Continue improving multiplayer flow, Firestore synchronization, and UI feedback.
- Ensure all edge cases (disconnects, forfeits, timeouts) are handled gracefully.
- Maintain clear, maintainable code and documentation for future contributors.

---

**For questions or further development, review MultiplayerActivity.java and this document.**

## matchmaking logic for multiplayer matches

- Once both players have entered each other's UIDs and pressed ready, a unique duel document is created in the `duels` collection. If it already exists, it is reused. This is checked via both user's documents' fields, if they both have each other's UIDs and `ready=true`.
- The duel document is named by a unique `duelId`, typically a combination of both UIDs.
- The first client to write the document becomes the host.
- The duel document contains fields: `player1`, `player2`, `createdAt`, `targetWord`, `verdict`, `3_sec_countdown_start_time`.
- The host is always player1, the other is player2. Only the host generates the target word and sets the `countdown_start_time` field to a server timestamp + small buffer (e.g., 3 seconds).
- Once all of the above is met: the 3 second countdown starts on both clients, synchronized to the same moment.
- If either player cancels before the countdown ends, the duel document's fields are reset except for `player1`, `player2` to ensure that the host remains the same.
- If both players remain ready after the countdown, the 3 second countdown ends and both players are navigated to the game page (GameActivity.java) with isMultiplayer as true.
- As soon as the game page loads, a synchronized 5 minute countdown starts for both players, again using a server timestamp + small buffer.
- Once either player finishes, their result is written to the duel document under their UID.
- the match can end in various ways: win, lose, draw, forfeit (if one player cancels before starting), timeout (if neither player finishes in time)
- The app listens for both players' results to determine the final outcome (win, lose, draw, forfeit, timeout).
- If neither player finishes within the 5 minute countdown, the game ends in a timeout for both players.
- When the game ends, a dialog shows the result (win, lose, draw, forfeit, timeout) based on the verdicts stored in the duel document. The dialog also displays the target word and an exit button leading to the previous page. 
- Once the game ends, both players' `ready` fields in their user documents are reset to false, allowing them to start a new match if desired. The duel document's fields are also reset except for `player1`, `player2` to allow for rematches without changing the host.
- the match can end in various ways: win, lose, draw, forfeit (if one player cancels before starting), timeout (if neither player finishes in time)

NOTE FOR ME: THE checkMatchmakingCondition is flawed. Check the countdown and host parts!!! The synced 3 second countdown also barely works. Fix it!!! 
MUST: Update both the multiplayer file and game file to use the correct fields and approach as described above.

Last note: I changed the function endGameWithVerdict so that a user can call it with a "forfeit" even if a result was submitted once. 