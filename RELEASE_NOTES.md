# Release Notes — Music Screensaver

## 0.1.15

- Add persisted HA select Tekstens musikreaktion: Fra, Diskret (default), Livlig. Kiosk command cycles the same setting without exceeding 20 settings.
- Bass pulses drive style-specific hop, tilt, scale, subtle stroke weight or spread. Text remains in its existing line layout. Pausing clears the reaction.
- Reuse Spectrum Visualizer's already delayed PARTY frames; do not add a second Sonos buffer. Timestamp the envelope at dispatch and reject demo frames and frames more than 250 ms old.
- Expose Lydsynkronisering from Spectrum's measured/estimated synchronization status. Physical speaker-to-display timing is not verified by unit tests.

## 0.1.14

- Add Words Fly, Words Drop and Letters Assemble lyric animations; include all in Random.
- Animate individual words or character clusters into their final readable layout. Short lines support letters; longer lines fall back to words.
- Finish entrances within 38% of the line interval, capped at 1.6 seconds, leaving a reading hold. Pause/seek follow playback position. This is choreography using line timestamps, not word-level karaoke.
- Reuse shaped text layouts, cap work for unusually long lines, and render the settled phrase once rather than per character.
- Add range and timing regression tests. Native visual appearance still requires device verification.

## 0.1.13

- Retry attaching a requested overlay on subsequent ticks instead of abandoning it after one 300 ms attempt.
- Add Visningsstatus with separate waiting, attached, and window-focus states. Active still represents the requested music session; focus does not prove physical visibility.
- Run configuration and command-driven UI changes on Android's main thread.
- Remove overlays from their actual parent and unregister lifecycle listeners on stop; prevent stopped instances rebuilding views after plugin reload.
- On-device activation and Fotoo foreground restrictions still need verification. Restart Kiosk once after updating to clear listeners retained by older versions.

## 0.1.12

- Fix playback time rewinding on repeated HA snapshots. Respect media_position_updated_at, pause/resume, seeking and track changes. This affects timed lyrics and vinyl light motion.
- Persist the complete settings map when choosing a visualization from HA, preserving the other settings.
- Adapt Twinkly bridge's relative bass-rise, hold and decay principle to incoming FFT bands. Apply explicit outward fluid impulses and expansion; widen gain response and reduce unrelated idle wandering. This is not the bridge's PCM spectral-attack analyzer.
- Add a Lyddata diagnostic text sensor with live bass/pulse values or missing-feed status.
- Add Backlit Marble, Backlit Sunburst and Backlit Translucent patterns and faster moving illumination.
- Add size-aware Native ferrofluid detail up to 1024 pixels. High resolutions cost CPU; measure on the actual display.
- Regression checks cover repeated snapshots, pause/resume, seek, track change, sustained tones and transient pulse retention.

## 0.1.11

- Fix a startup/hidden-overlay crash when HA reports volume before a MusicVisualView exists. Remove unrelated quality/lighting updates from both volume handlers; apply them only when creating/configuring the view.
- Add a focused regression harness that executes the production volume-update block with a missing view, a live view, and a view removed between updates.

## 0.1.10

- Rebuild lyric layout when changing visualization in plugin settings while the overlay is open; previously only the background renderer changed.
- Includes all 0.1.8 and 0.1.9 improvements below.

## 0.1.9

- Refine the new backlit vinyl texture with irregular, smoothly warped pigment islands instead of periodic grain.
- Includes the 0.1.8 improvements below.

## 0.1.8

- Increase ferrofluid surface detail from 144 to 384 pixels by default, with Eco 192 and Ultra 640 options; scale edge smoothing with resolution.
- Preserve plain HA lyrics, discard outdated lookup responses after track changes, include album metadata and add a conservative LRCLIB search fallback. Show lookup failures/no-result/instrumental states instead of waiting forever.
- Add lyric composition selection: Random, Serif, Stacked, Diagonal, Contrast. Random avoids immediately repeating the same composition.
- Add Backlit Splatter vinyl style and Music, Flow or Album lighting. Stationary procedural pigment with moving illumination and album label, inspired by the supplied Vylumi clip. No tonearm in this style.
- Observe Android Dream start/stop events and retain the music session when Kiosk's own screensaver is dismissed. Keep the screen awake while the visible music overlay overrides screensavers. Bringing the app forward remains subject to Android background-launch restrictions; this cannot forcibly stop another app's DreamService.
- Device rendering, frame rate and native Fotoo takeover still require on-device verification.

## 0.1.7 — first reference-driven revision

- Replace fixed six-lobe contour and orbital dots with viscous moving masses and a mergeable implicit surface.
- Remove the circular bezel and glass wedge; use a softly illuminated cavity.
- Remove synthetic 124 BPM fallback. Missing live audio is indicated explicitly.
- Replace scrolling lyrics with four phrase-based typographic compositions: centered serif, stacked condensed, staggered diagonal, contrasting scales.
- Interpolate line timestamps between player updates and freeze on pause. No invented word timing.
- Add fluid stability and surface-topology checks. Physical-device appearance, sound alignment and performance still require validation.

## 0.1.6

- **Authentic Vinyl Player Aesthetic (`home-assistant-vinyl-player`)**:
  - **Prominent & Bright Album Art**: Fixed a paint alpha leak where the center label bitmap was inadvertently drawn with 7.8% opacity. Cover art is now rendered with 100% full vibrant contrast, bilinear filtering, and an expanded radius (44% of disc).
  - **Photorealistic Drop Shadow**: Deep multi-pass feathered drop shadow beneath the vinyl disc, giving the appearance of floating above the card.
  - **Realistic Tonearm & Stylus**: Modeled a sleek metallic gimbal pivot at top-right with tubular arm, angled cartridge headshell, and needle resting on the outer groove while playing, and resting lifted off the disc when paused.
  - **Saturated Album-Tinted Matte Backdrop**: Dynamically samples highest-saturation color tones from album cover art to generate an elegant matte card background with soft lighting vignette.
  - **Integrated Typography & Translucent Pill Controls**: Displays crisp white song title and muted artist name centered beneath the disc, alongside translucent circular playback pills (`|<<`, `||`, `>>|`) matching `home-assistant-vinyl-player`.
  - **Smooth Volume Slider**: Interactive horizontal volume slider bar with speaker icons, filled progress track, and white knob thumb supporting real-time touch dragging.
- **Organic Dancing Ferrofluid Overhaul (SPIROK ROBO / MagnePulse Inspired)**:
  - **Illuminated Glass Porthole Chamber**: Encased the ferrofluid inside a sleek circular display cell featuring a metallic bezel frame, bright backlit white interior (or neon cyan/magenta), inner glass bevel shadow, and subtle front glass reflection arc.
  - **Living Symbiote Blob Morphology**: Replaced the 28-spoke geometric "sea-urchin" star with an organic 6-harmonic fluid body governed by surface tension. Tentacles surge, stretch, and snap back organically to the beat with bulbous rounded tips (no straight vector stick lines).
  - **Wet 3D Liquid Specular Highlights**: Natural spherical dome sheen and fluid glints conforming to the liquid blob curves.
  - **Rayleigh-Plateau Liquid Beads**: High bass transients dynamically pinch off obsidian beads that hover near the glass wall before re-merging.
- **Synchronized Lyrics Overhaul (lrclib.net Integration)**:
  - If Home Assistant entity attributes do not provide synchronized lyrics, the plugin automatically queries the open-source `lrclib.net` database for live synced LRC lyrics in the background.
  - Kinetic synchronized scrolling highlights the active singing line in bold cyan glow, while past and upcoming lines softly fade.
  - Graceful fallback display with musical note and track info if a song is instrumental or has no lyrics found.
- **Robust HA Proxy Artwork & Token Resolution**:
  - Automatically resolves Home Assistant base URLs and authorization tokens from `FlutterSharedPreferences` or Music Assistant bridge, ensuring `/api/media_player_proxy/...` artwork loads reliably even behind authentication.
  - Responsive vertical split on portrait displays for hybrid vinyl/ferrofluid modes.

## 0.1.5

- **True Acoustic & Magnetic Ferrofluid Physics**:
  - **Rosensweig Needle Cones**: Replaced circular sine wobbles with genuine magnetic normal-field instability spikes. Flanks are rendered with concave bezier curves meeting at razor-sharp needle tips along the magnetic vector.
  - **Specular Liquid Chrome Ridge Lines**: Rendered 3D metallic wet gloss highlights running along the spine of every active spike, glistening as the cones shoot outward.
  - **Rayleigh-Plateau Satellite Droplets**: Heavy kick drums pinch off liquid obsidian satellite beads that shoot outward with independent specular glints before snapping back under liquid surface tension.
  - **Polar Magnetic Alignment & Center-of-Mass Surging**: Fluid leaps vertically off the baseline with kick drum impact, concentrating primary spikes at the top and bottom electromagnetic poles.
- **Spectrum Visualizer Broadcast Bridge**:
  - Automatically registers `party_mode_presentation` and broadcasts `me.jxl.kiosk.plugins.PARTY_PRESENTATION_CHANGED` whenever the music screensaver is active. This wakes up Kiosk Satellite's `SpectrumVisualizerPlugin`, activating live 32-band FFT audio frame streaming at 25 FPS via `PARTY_AUDIO_FRAME`.
- **Autonomous Track-Locked Beat Synthesizer (Zero-Stall Fallback)**:
  - If live audio frames from Sendspin are delayed or silent, an autonomous acoustic tempo engine drives kick, snare, bass groove, and hi-hats synchronized to track playback time. The ferrofluid dances dynamically to the song at all times, never stalling or sitting as a flat circle.
- **Album Matched Color Support**:
  - Dynamically samples the dominant color palette from album artwork for the ambient magnetic aura glow when "Album Matched" is selected.

## 0.1.4

- **Fix App Crash on Button Tap (`Invalid host request`)**:
  - Replaced unsupported `callHaService` and `bringToFront` host commands with direct Music Assistant HTTP API calls (`player_queues/play`, `player_queues/pause`, `player_queues/next`, `player_queues/previous`, `players/cmd/group_volume`).
  - In Kiosk Satellite, passing an unhandled command string to `host.executeCommand` caused `IllegalArgumentException: Invalid host request`, terminating the Kiosk process and causing Kiosk Satellite to disable the plugin upon restart.
  - Playback buttons (Play/Pause, Next, Previous, Volume) now directly command Music Assistant using the shared authentication token and active queue without triggering any host errors.
  - Dismissing the overlay safely wraps `stopScreensaver` in a guarded try-catch block.

## 0.1.3

- **Fixed HA Switch & Select Event Delivery (`onEvent`)**:
  - Implemented event listener in `onEvent` for `switch.active` and `select.effect`. When toggling the switch in Home Assistant, Kiosk Satellite delivers state updates via `onEvent`, which previously was unhandled and caused the switch to immediately flip back to off.
  - Turning the switch on in HA now unconditionally sets `manualActive = true`, immediately brings Kiosk Satellite to the front, and renders the music screensaver overlay.
  - Handled `select.effect` events from HA so selecting visualizations from Home Assistant changes the effect instantly.
  - Ferrofluid and Cotodama ambient effects now animate continuously even when playback is idle or paused.

## 0.1.2

- **Direct Bidirectional HA Switch Control**:
  - Flipping `switch.raspberry_raspberry_pi_4_music_screensaver_aktiv` to `on` now immediately forces the music screensaver to show and stay on, regardless of `activationMode` or playback status.
  - Tapping anywhere on the background canvas dismisses the screensaver and instantly switches the HA switch back to `off`.
  - When the screensaver activates automatically during playback, the switch turns `on` to reflect live status; when playback stops or screensaver exits, it turns `off`.
- **Robust Activity Discovery & Foreground Bring-To-Front**:
  - Resolved `ActivityThread.mActivities` reflection fallback so Kiosk Satellite's Activity is never missed even if the plugin started while the app was already running.
  - Automatically brings Kiosk Satellite to the foreground via Intent reorder when activating or forcing the screensaver on.
  - Removed premature `stopScreensaver` invocation from periodic postpone loop, ensuring screensaver state remains stable.
- **Clean Entity Naming**:
  - Published switch and select titles now avoid duplicate plugin prefixes, creating clean entities `Music Screensaver: Aktiv` and `Music Screensaver: Visualisering`.

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
