# 0.1.21

- **Organic Dancing Ferrofluid (Non-Symmetric)**: Replaced rigid star geometry with an organic, swirling magnetic fluid model. Features an anchored nucleus, orbiting dynamic lobes, and satellite tendrils that stretch into asymmetric shapes, split into separate droplets on drum kicks, and coalesce smoothly back under surface tension during quiet passages.
- **Home Assistant Telemetry & Remote Debugger**:
  - Published dedicated `audio_debug` text sensor reporting full diagnostic telemetry (track position, Sonos delay, frame age, last beat hit, bass/mid/treble, and pulse) directly to Home Assistant for effortless AI/developer verification.
  - Published Home Assistant switch `debug_hud` (`switch.raspberry_raspberry_pi_4_music_screensaver_vis_synk_debugger_pa_skaerm`) allowing remote toggling of the on-screen HUD without any touch hotspots or screen tap triggers.
  - Removed on-screen touch hotspots so the display remains completely clean and immune to accidental taps.

# 0.1.20

- **Ferrofluid Magnetic Modeling**: Completely overhauled `FluidModel` to anchor the droplet at center `(0, 0)` with 8 peripheral magnetic lobes. Replaced aimless Brownian wandering with authentic radial Rosensweig spikes that punch outward synchronously with drum kicks and basslines, relaxing back into a smooth droplet under surface tension during quiet passages.
- **Dynamic Bass Envelope Tracking**: Resolved baseline saturation lockout in `AudioEnvelope` by applying non-linear power expansion (`pow(bass, 2.5)`) and adaptive threshold tracking, ensuring kicks and snare transients register reliably without locking out during loud music.
- **Sync & Beat Debugging System**:
  - Added on-screen live telemetry HUD (toggled by tapping top-left corner or executing command `toggleDebugHud`). Displays exact track timestamp (`mm:ss.SSS`), effective Sonos SpeakerSync buffer delay (ms), frame age, and a flashing beat indicator dot that pulses green on every beat hit.
  - Upgraded Home Assistant text sensor `audio_status` to report track time, last beat hit timestamp, beat strength, smoothed bass, and applied delay.
  - Added timestamped beat logging in Android logcat (`BEAT HIT: track=... wall=... str=...`).

# 0.1.19

Dynamic beat reactivity and asymmetric envelope tracking: improved transient response and pulse triggering in AudioEnvelope with asymmetric attack/decay baseline so ferrofluid pulses sharply to kicks and bass hits even during loud or sustained passages.

# 0.1.18

The existing fluidQuality setting is now “Detaljegrad · ferrofluid og vinyl”. Existing values and default High are preserved. Eco/High/Ultra/Native also set backlit texture resolution to 256/640/1024/1536 and vinyl groove counts to 8/14/28/40 (backlit: 24/65/96/128). Texture regeneration happens only on quality/pattern change. Text and covers stay sharp. FPS, gain and speaker delay are unchanged. Still 20 settings.
