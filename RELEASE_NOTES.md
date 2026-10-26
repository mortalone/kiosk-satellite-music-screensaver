# 0.1.23

- **Anti-Double-Trigger Beat Tracking**: Extended minimum beat refractory hold-off to 230ms (~260 BPM max) and added transient attack onset flux validation (`onset >= minAttackFlux`). Eliminates sub-bass tail double-triggering and false swells on sustained bass notes that previously caused the visualizer to flutter at 270 BPM and lose synchronization during dense passages.
- **Kick Drum Frequency Isolation**: Re-weighted raw bass input to focus on bands 1 & 2 (55–80 Hz kick punch fundamental), isolating physical kick drum impacts from low-mid bass guitars, vocal resonances, and synth pads.
- **Continuous Harmonic Fluid Flow ("Musikstrømmen")**: Added undulating harmonic wave motion driven by chords and continuous bass, ensuring the ferrofluid never freezes dead between beats. Reduced artificial viscous over-damping so droplets oscillate with natural fluid inertia and bounce elastically to the tempo.

# 0.1.22

- **Lockout-Free Dual Envelope Follower**: Replaced single-baseline tracking with an adaptive dual-envelope system (fast-attack peak follower and floor/trough tracker) with guaranteed dynamic headroom (`floor <= 0.55 * peak`). Completely eliminates beat lockout during loud or sustained passages, ensuring crisp beat detection on every kick drum across all musical genres.
- **Musical Velocity Stabilization**: Calibrated magnetic vortex and droplet orbit velocities to natural musical tempos (~8-18 RPM), ending hyper-speed rotational blur. Input frequency bands to fluid physics are cleanly normalized, preventing high user gain multipliers from turning the fluid into a chaotic blender.
- **Beat-Reactive Droplet Splitting & Snapping**: Tied beat transients (`kick` and `pulse`) directly to radial stretch and asymmetric tendril release. On heavy kick drum punches, droplets burst outward and split into isolated satellites, snapping back elastically under magnetic surface tension as the transient decays.
- **Enhanced Telemetry Counter**: Added live beat counter `#%d` to `audio_debug` Home Assistant sensor (`sensor.raspberry_raspberry_pi_4_music_screensaver_lydsynk_telemetri`), allowing instant verification of continuous beat detection directly from Home Assistant.

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
