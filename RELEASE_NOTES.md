# Release Notes — Music Screensaver

## 0.1.1

- Add **`activationMode`** setting with 3 distinct options:
  - `When screensaver starts`: Acts as a true screensaver. Waits for Kiosk Satellite's native screensaver idle timeout or manual screensaver button. Activates only when music is playing, seamlessly overruling Fotoo/Nature Frame.
  - `Immediately when playing`: Activates as an instant music overlay as soon as music playback starts.
  - `Manual / HA switch only`: Only activates when turned on manually or via Home Assistant entity.
- Subscribe to Kiosk Satellite `screensaver.state` and `screensaver.view` events and query initial `isScreensaverActive`.
- Tapping on the background canvas dismisses the screensaver and commands Kiosk Satellite to stop the screensaver, immediately returning to the Lovelace dashboard.

## 0.1.0

- Initial release of **Music Screensaver** for Kiosk Satellite.
- **Vinyl Player (`vinyl`)**: Hardware-accelerated 2D rotating vinyl turntable at 33⅓ RPM with circular album art center label, concentric rille micro-grooves, dynamic rotating light reflection sheen, metallic brass spindle hole, and bass-reactive ambient backlight glow.
- **Ferrofluid (`ferrofluid`)**: Sound-reactive magnetic liquid blob dynamically reacting to FFT bass and mid-frequency bands with smooth Catmull-Rom closed splines, deep obsidian black body, specular wet gloss highlight, and beat-activated satellite droplets.
- **Cotodama Kinetic Lyrics (`cotodama`)**: Animated typography inspired by Cotodama Lyric Speaker, synchronizing live with Music Assistant LRC lyrics with energy scale pulse, text glow, and ambient bottom audio wave.
- **Hybrid Split-Stage (`hybrid_vinyl` & `hybrid_ferrofluid`)**: Landscape widescreen split layout placing the hero visual (Vinyl or Ferrofluid) on the left stage and live kinetic synchronized lyrics on the right stage.
- **Smart Screensaver & Overrule Rules**:
  - Automatically activates when the selected media player starts playing.
  - Overrules default screensavers (Fotoo / Nature Frame) while music is playing.
  - Yields and allows the default screensaver to take over when music stops or is paused.
- **Touch Interaction**:
  - Playback controls (Play, Pause, Skip, Previous, Volume) operate playback without closing the screensaver.
  - Tapping anywhere on the canvas or background immediately dismisses the screensaver and returns directly to the Lovelace dashboard.
- **Customization Options**:
  - Choice of Vinyl display style (`With Ambient Glow` vs `Disc Only (Minimal)`).
  - Choice of Ferrofluid color theme (`Obsidian Black`, `Cyber Cyan`, `Neon Magenta`, `Album Matched`).
  - Background styling (`Album Tinted`, `Pure Black`, `Deep Charcoal`).
- **Raspberry Pi 4 Optimized**:
  - Pure 2D canvas drawing with zero per-frame garbage collector allocations.
  - Runs silk-smooth at 25–30 FPS without GPU or CPU choke.
- Strictly adheres to Kiosk Satellite's limit of maximum 20 settings and 20 commands.
