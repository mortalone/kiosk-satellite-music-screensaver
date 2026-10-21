# Music Screensaver for Kiosk Satellite

A high-aesthetic, hardware-accelerated music screensaver and ambient now-playing display for Kiosk Satellite tablets and wall displays (including Raspberry Pi 4 touchscreens).

![Music Screensaver](https://raw.githubusercontent.com/mortalone/kiosk-satellite-music-screensaver/main/screenshot.png)

## Features

- **Vinyl Player**: Realistic 33⅓ RPM rotating vinyl turntable with circular cropped album artwork as the center label, concentric rille micro-grooves, rotating anisotropic sheen reflection, brass spindle hole, and bass-reactive backlight glow.
- **Ferrofluid**: Sound-reactive magnetic liquid blob reacting dynamically to real-time FFT audio bands with Rosensweig spikes, liquid obsidian depth, specular glossy highlights, and beat-activated satellite micro-droplets.
- **Cotodama Kinetic Lyrics**: Animated typography inspired by the *Cotodama Lyric Speaker*, synchronizing live to Music Assistant LRC lyrics with bass energy scale pulsing and glowing text shadows.
- **Hybrid Split-Stage**: Split landscape widescreen view showing the visual hero (Vinyl or Ferrofluid) on the left side with live kinetic synchronized lyrics on the right side.
- **Smart Screensaver Overrule**:
  - Overrules default screensavers (Fotoo / Nature Frame) while music is playing.
  - Automatically yields and lets the default screensaver take over when music stops.
- **Touch-Friendly Controls**:
  - Tapping playback or volume controls operates the queue without dismissing the screensaver.
  - Tapping anywhere on the canvas or background dismisses the screensaver immediately and returns directly to your Home Assistant dashboard.
- **Smooth 25–30 FPS on Raspberry Pi 4**:
  - Built with pure Android hardware-accelerated 2D Canvas routines.
  - Zero per-frame memory allocation for stutter-free playback without garbage collection spikes.

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