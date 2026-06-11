# Global Vision Lounge QA Checklist

## Verified In Current Environment
- `./gradlew :app:assembleDebug`
- `./gradlew :app:assembleRelease`
- `./gradlew :app:lintDebug`

## Artifacts
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Real Device Verification

### Install
- Install debug APK on at least one 1080p Android TV device.
- Install debug APK on at least one 4K Android TV device.
- Confirm launcher icon and TV banner render correctly on the TV launcher.
- Confirm app launches from both `LEANBACK_LAUNCHER` and regular launcher entry.

### Home
- Verify left-side tab navigation is reachable and focus never disappears.
- Verify search entry jumps into the search page and back navigation returns correctly.
- Verify category filter changes trigger updated content and user-facing status prompts.
- Verify pagination loads more data near the end of the grid without overlap or clipped cards.
- Verify empty state messaging is visible when a category or filter returns no content.

### Search
- Verify keyword search returns data and top result receives focus correctly.
- Verify clear action returns focus to the input field.
- Verify pagination on long result sets loads more items without layout jumps.
- Verify empty and failure states are readable on TV from couch distance.

### Detail
- Verify source switching updates selected source and status prompt.
- Verify episode switching launches playback for the selected episode.
- Verify very long source lists and episode lists remain scrollable and focusable.
- Verify no synopsis, metadata, or poster region overlaps on 1080p or 4K.

### Player
- Verify D-pad left/right seeks, center toggles play/pause, and back first shows controls then exits.
- Verify `MediaNext` and `MediaPrevious` hardware keys switch episode when supported by the remote.
- Verify source switching and episode switching work inside the player overlay.
- Verify previous/next episode shortcut buttons work and show boundary prompts on first/last episode.
- Verify buffering, retry, and playback failure prompts are understandable and dismiss naturally.
- Verify long source and episode lists in the overlay remain scrollable and are not clipped.

### Visual / Layout
- Verify text remains legible from couch distance on both 1080p and 4K.
- Verify focus borders are always visible over posters, chips, and control buttons.
- Verify no card, chip, hero block, or overlay is cut off by safe-area or screen edge.
- Verify spacing remains balanced across Home, Search, Detail, and Player.

## Known Remaining Non-Blocking Warnings
- Dependency version update suggestions from lint.
- AGP / Compose custom lint version mismatch warnings from current toolchain.
- External BouncyCastle trust-manager warning reported from dependency jars rather than app code.
