# 0.1.16 — manual display priority

Manual activation now holds the music view independently of the automatic override setting, including when music pauses or tracks change. Native Kiosk screensaver starts yield to the manual view. The screen stays awake for this manual session.

Explicit HA off / dismissal of a manual view dismisses Kiosk once and re-arms its normal idle timer without changing the saved screensaver mode. Music auto-start is inhibited for the rest of the plugin session until an explicit on (or plugin restart), so track/idle events cannot immediately take the screen back. A per-screen HA coordinator must re-activate music when Music Screensaver is the configured default; this plugin does not infer HA helper IDs.

No permanent screensaver setting or external app is disabled. Physical kiosk/HA integration testing is still required.
