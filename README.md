# Music Screensaver for Kiosk Satellite

A high-aesthetic, hardware-accelerated music screensaver and ambient now-playing display for Kiosk Satellite tablets and wall displays (including Raspberry Pi 4 touchscreens).

![Music Screensaver](https://raw.githubusercontent.com/mortalone/kiosk-satellite-music-screensaver/main/screenshot.png)

## Features

- **Vinyl Player**: Realistic 33⅓ RPM rotating vinyl turntable with circular cropped album artwork as the center label, concentric rille micro-grooves, rotating anisotropic sheen reflection, brass spindle hole, and bass-reactive backlight glow.
- **Ferrofluid**: Sound-reactive magnetic liquid blob reacting dynamically to real-time FFT audio bands using moving viscous masses and a shaded implicit surface that can merge and separate. A soft illuminated cavity replaces the bezel. This is a visual approximation, not a physical ferrofluid solver.
- **Cotodama Kinetic Lyrics**: Animated typography inspired by the *Cotodama Lyric Speaker*, synchronizing live to Music Assistant LRC lyrics as one phrase at a time in four selectable typographic compositions, including a Random option. Line timestamps control phrase changes; word entrances are stylistic, not word-level karaoke.
- **Hybrid Split-Stage**: Split landscape widescreen view showing the visual hero (Vinyl or Ferrofluid) on the left side with live kinetic synchronized lyrics on the right side.
- **Smart Screensaver Overrule**:
  - Overrules default screensavers (Fotoo / Nature Frame) while music is playing.
  - Automatically yields and lets the default screensaver take over when music stops.
- **Touch-Friendly Controls**:
  - Tapping playback or volume controls operates the queue without dismissing the screensaver.
  - Tapping anywhere on the canvas or background dismisses the screensaver immediately and returns directly to your Home Assistant dashboard.
- **Rendering budget**: Ferrofluid reuses a 384×384 surface texture by default (selectable 192 or 640). Device FPS and thermals still need measurement; no Raspberry Pi performance guarantee.
- **Honest audio fallback**: Without live FFT frames, the fluid relaxes and shows “Afventer live lyddata”. It does not invent a beat. Real reactivity requires the existing Spectrum Visualizer audio feed.

See [visual direction and acceptance checks](docs/VISUAL_DIRECTION.md) for the reference analysis and remaining device validation.

## Installation

In Kiosk Satellite:
1. Open **Settings** → **Plugins** → **Install plugin from URL**.
2. Enter the latest release ZIP URL from [Releases](https://github.com/mortalone/kiosk-satellite-music-screensaver/releases).
3. Select your speaker entity (e.g. `media_player.stueetagen_visualizer`).
4. Choose your favorite visualization mode under **Visualisering**.

## Settings

| Setting | Type | Description |
| --- | --- | --- |
| `speakerEntity` | entity | Media player to track (e.g., `media_player.stueetagen_visualizer`). |
| `startAutomatically` | boolean | Automatically show screensaver when music plays. |
| `onlyWhilePlaying` | boolean | Hide screensaver when playback stops so standard screensaver can take over. |
| `showPaused` | boolean | Allow screensaver while playback is paused. |
| `effect` | select | `hybrid_vinyl`, `hybrid_ferrofluid`, `vinyl`, `ferrofluid`, `cotodama`. |
| `vinylStyle` | select | `With Ambient Glow` or `Disc Only (Minimal)`. |
| `ferrofluidColor` | select | `Obsidian Black`, `Cyber Cyan`, `Neon Magenta`, `Album Matched`. |
| `backgroundTheme` | select | `Album Tinted`, `Pure Black`, `Deep Charcoal`. |
| `showPlaybackControls` | boolean | Show/hide Play/Pause and Next/Prev buttons. |
| `volumeControls` | select | `Off`, `Buttons`, `Slider`. |
| `tapDismiss` | boolean | Tap anywhere on background to return to dashboard. |
| `gain` | number | Audio visualization sensitivity (1–10). |
| `refreshRate` | select | `15 FPS Eco`, `25 FPS Normal`, `30 FPS Smooth`. |
| `overruleScreensaver` | boolean | Postpone default screensavers while music is playing. |
| `visibilityEntity` / `condition` / `value` | entity / select / string | Optional Home Assistant visibility gate. |

## Build

```sh
python3 tools/build.py --android-platform 35
```

## License

MIT © mortalone
## New visual settings (0.1.8)

Select **Vinyl visning → Backlit Splatter** for the illuminated pressing. It works in vinyl and hybrid vinyl modes. **Gennemlyst vinyl: lys** selects Music, Flow or Album illumination. The pattern is generated locally, not copied product imagery.

**Tekstanimation** selects Random, Serif, Stacked, Diagonal or Contrast. It only controls appearance; it cannot create missing lyric data. Select cotodama or a hybrid visualization to display lyrics. Untimed lyrics show a labeled static excerpt.

**Ferrofluid detaljegrad** selects Eco (192), High (384, default), or Ultra (640). Higher resolution increases CPU work; actual performance must be checked on the device.

### Native Android Dream / Fotoo

Android Dream is separate from Kiosk's internal screensaver. The plugin observes Dream start/stop broadcasts and requests that Kiosk move forward when music is eligible. Android can reject background launches. No hidden API, force-stop or secure-setting modification is used. An existing Dream that started before the plugin registered may not produce a new start event.

If an HA automation starts Fotoo, add a condition to that automation to skip starting it while the music-screensaver active entity is on. A separate existing supported action to end the native Dream may still be needed. Entity/action IDs must come from the user's actual automation; do not invent them. Stopping Kiosk's internal screensaver alone is not proof that Fotoo's Dream ended.

### 0.1.12 playback and visualization

HA visualization selections are saved with the full plugin configuration. Timed lyrics follow a monotonic estimate anchored to HA's position timestamp; plain text remains explicitly untimed and cannot be synchronized automatically.

The **Lyddata** text sensor shows whether live FFT bands arrive and the measured bass/pulse. No live audio means gain cannot create real synchronization. Bass onset hold/decay is adapted from the Twinkly bridge, but the screensaver only has FFT bands, not the bridge's scheduled raw PCM.

Vinyl style now also offers **Backlit Marble**, **Backlit Sunburst**, and **Backlit Translucent**. Light modes continue to be Music, Flow and Album. **Native** fluid quality uses the displayed diameter up to 1024 pixels; it is a CPU raster and may reduce frame rate on a Pi. A 27-inch diagonal alone does not determine required pixel resolution.
