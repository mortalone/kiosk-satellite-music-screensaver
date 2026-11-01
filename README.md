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

### Individual word and letter entrances (0.1.14)

Choose **Tekstanimation → Words Fly / Words Drop / Letters Assemble**, or **Random** to mix these with the four existing compositions. Letters Assemble uses character clusters for lines of up to 48 code points and words for longer lines. The entire phrase settles within at most 1.6 seconds (38% of the line interval for short intervals). Exact sung-word timing is not inferred. Untimed lyrics remain a labeled static excerpt after their entrance.

### Music-reactive typography (0.1.15)

In HA select **Tekstens musikreaktion → Fra / Diskret / Livlig**. Default is Diskret. The plugin command with that name cycles the same persisted choice. This avoids adding a 21st plugin setting.

Stacked/Words Drop hop; Diagonal tilts; Serif/Contrast scale (Contrast also subtly thickens); Words Fly/Letters Assemble spread around the center. No reflow occurs on beats.

Synchronization review: Twinkly's sendspin_source.py schedules timestamped frames against the Sendspin client clock. Spectrum Visualizer's publishDigitalFrame instead applies SpeakerSync's Sonos delay BEFORE broadcasting PARTY_AUDIO_FRAME. Its `at` value is dispatch elapsedRealtime, not a future playback deadline. The screensaver therefore must not apply the Sonos buffer again. It rejects demo/stale frames and ages pulses from dispatch. Lydsynkronisering exposes the producer's sync_status_summary (LOCKED/ESTIMATED/etc.); cached status is not proof of a current acoustic lock. Without upstream speaker synchronization, actual physical alignment remains unverified. Rendering and display scanout add latency; no acoustic calibration is claimed.

## Justerbar detaljegrad (0.1.18)

The existing fluidQuality setting is now “Detaljegrad · ferrofluid og vinyl”. Existing values and default High are preserved. Eco/High/Ultra/Native also set backlit texture resolution to 256/640/1024/1536 and vinyl groove counts to 8/14/28/40 (backlit: 24/65/96/128). Texture regeneration happens only on quality/pattern change. Text and covers stay sharp. FPS, gain and speaker delay are unchanged. Still 20 settings.


### Backlit vinyl · 0.1.30

Choose **Vinyl visning** while using Vinyl or Hybrid Vinyl:

| Design | Material |
| --- | --- |
| Backlit Splatter | Irregular dark pigment islands and illuminated openings |
| Backlit Mosaic | Cyan and amber glass-like chips with dark borders |
| Backlit Sunburst | Irregular radial streaks of varying lengths |
| Backlit Marble | Warped flowing pigment veins |
| Backlit Colour Sectors | Broad contrasting sectors with splatter |
| Backlit Translucent | Quiet translucent pressing with a luminous rim |

**Gennemlyst vinyl: lys → Music** adds pulses from the same synchronized beat envelope used by ferrofluid. Bass controls sustained light and treble adds local illumination. A live audio source is required for those reactions; colour flow itself is decorative. **Flow** slowly changes colour; **Album** holds the album hue. As of 0.1.32, the material has gentle local deformation and travelling illumination in all three modes; the centre label stays stationary. No further HA helpers or settings are needed.

Detail quality still applies to all six materials. Generation happens off the UI thread; when changing style or quality, the previous material remains briefly until the replacement is ready. These are procedural interpretations of illuminated vinyl, not photographs of a product.


### Living wall art · 0.1.31

Update the plugin and choose **Visualisering** in Kiosk or the existing Home Assistant select:

| Value | Scene |
| --- | --- |
| `bloom` | A layered flower with folded petals and a gently opening silhouette |
| `silk` | Floating ribbons with soft highlights |
| `chrome` | An organic metallic form with moving reflected light |
| `aurora` | Soft curtains of light and sparse stars |
| `coral` | Branching luminous coral with slow sway |
| `sand_table` | A kinetic sand table: four balls draw persistent grooves along interwoven rosettes |
| `prism_party` | Coloured beams and layered prisms with stronger beat-driven geometry |

These scenes are generated live. They retain gentle decorative motion without music; audio adds a bounded response through the same synchronized envelope as the existing effects. Sand only changes speed slightly, retaining the calm physical-table character. Its initial rosette is drawn procedurally; the four moving balls continue drawing, and the surface gradually clears after 20 minutes. Switching away freezes sand progression; restarting the overlay starts a fresh table.

**Detaljegrad · visualiseringer** controls mesh density, coral branching and sand resolution (384/768/1024/1280). Start at **High** on the Pi and use **Eco** if needed. Screen diagonal is not pixel resolution. Existing playback/volume controls can be hidden for a cleaner picture-frame appearance. No new HA helpers are required. Older Android versions use a simpler triangle-rendering fallback.

`python tools/preview_art.py /tmp/art-preview.png` generates a composition check from the production renderer using a Java2D adapter. This is not an Android or GPU benchmark and approximates per-vertex shading. Test actual smoothness and colour on the target display before judging performance.

### Material and motion revision · 0.1.32

Splatter, Mosaic and Colour Sectors now share cellular optical detail, while retaining distinct pigment distributions. Light is applied to the textured surface, with moving local highlights and subtle mesh deformation. Sunburst, Marble and Translucent use the same animated lighting. Bloom now uses broad three-dimensional ruffled petals, fine veins and directional surface lighting. Sand has four pens and interwoven finer grooves. Decorative motion and synchronized music response remain separate: a moving scene alone does not demonstrate audio synchronization.

For a software composition check: `python tools/preview_art.py /tmp/vinyl.png vinyl 8` renders vinyl at eight seconds; `python tools/preview_art.py /tmp/art.png landscape 8` checks art geometry at that time. These snapshots do not measure playback frame rate or test Android rendering. Start with High; the denser flower surface is more expensive than the previous simplified petals. Evaluate Eco if necessary on the target device.
