# 0.1.32

- Reworked backlit materials: Splatter and Colour Sectors now have individual glass-like chips and bevels. Animated mesh deformation and travelling local illumination affect the material itself across all six styles; the centre label stays still. Decorative motion continues independently of beats, while Music mode adds the existing synchronized audio response.
- Rebuilt Bloom with broad ruffled 3D petal surfaces, surface-normal lighting, radial veins and a detailed seed centre. Petals open, curl and ripple continuously; bass and beat add bounded movement.
- Sand Table now has four metallic balls drawing interwoven fine rosettes into retained sand. The fastest pen limits progression to avoid speed spikes at tight turns. Detail resolutions are 384/768/1024/1280.
- Silk and Aurora have denser meshes and flowing fine threads; Coral has moving branch curvature; Prism Party adds drifting coloured motes.
- Vinyl animation uses monotonic wall time so track seeks do not reset the artwork. Cached textures and reusable mesh buffers are retained. No additional settings or HA helpers.
- Verified full-source Android API compilation, bounded multi-ball motion, finite changing petal geometry, material detail, and existing overlay lifecycle tests. Inspected software-rendered compositions at different times. The Java2D preview approximates Android shading; actual device frame rate and final display appearance have not been measured.

# 0.1.31

- Seven live procedural scenes: Bloom (folded flower), Silk, Chrome, Aurora, Coral, Sand Table and Prism Party. Available in both plugin Visualisering and the existing HA select; still 20 settings and 11 commands.
- Wall-art scenes use a calm independent motion clock and bounded audio response. Music changes folds, reflections, light or motion; paused audio decays smoothly. No video loops or external assets.
- Kinetic sand table: a seeded rosette and a metallic ball continuously drawing retained grooves, with restrained music-dependent speed and a gradual rake after 20 minutes of viewing.
- Prism Party uses rotating coloured beams and geometric prisms with beat-driven expansion; no full-screen strobing.
- Existing detail setting now covers all scenes; cached geometry buffers and bounded sand texture sizes. Legacy Android hardware Canvas uses a triangle fallback. Existing synchronized audio timing is reused unchanged.
- Added motion/sand continuity tests and a Java2D composition preview tool. Device frame rate and the actual Android appearance still need on-screen evaluation.

# 0.1.30

- Rebuilt backlit vinyl around stationary pigment and annular transmitted light: bright near the label, smoky at the rim, with soft local illumination instead of a rotating rainbow wheel.
- Six selectable pressings in **Vinyl visning**: refreshed Splatter, Marble, Sunburst and Translucent; new Mosaic (cyan/amber chips) and Colour Sectors.
- Music light mode uses the existing synchronized beat envelope for ring expansion and illumination, bass for sustained energy, and treble for local highlights. Flow and Album remain non-beat modes. Audio transport and speaker-delay handling are unchanged.
- Pigment generation now runs on one low-priority worker, discards superseded work and keeps the previous texture until ready. Cached shaders, colour filters and textures avoid per-frame pixel generation. Existing Eco/High/Ultra/Native quality levels remain; no additional settings.

# 0.1.25

- **Pure Beat Reactivity & Synthetic Wave Elimination**: Removed synthetic sine oscillation (`flowWave`) that moved to an artificial mathematical clock detached from the music. The ferrofluid is now 100% physically driven by live incoming beat transients (`pulse`) and real frequency energy (`bass`/`mid`).
- **Unrestricted Transient Sensitivity**: Removed the artificial `minAttackFlux` gate that previously filtered out 85% of real drum hits (dropping BPM to 17-32 BPM). Restored natural rising-edge detection (`diff > 0 && linBass > previous`) with a clean 180ms hold-off (~333 BPM max) to prevent sub-bass double-triggering while capturing every musical beat.
- **Dynamic Droplet Ejection**: Enhanced radial pulse stretch (`pulse * 1.05`) so the outer fluid lobes actively punch outward, split into separate droplets on drum kicks, and snap back elastically under surface tension.

# 0.1.24

- **Eliminated 450ms Hard Cutoff Cliff**: Removed premature `elapsed > 450ms` pulse zeroing in `AudioEnvelope.pulse()`. Songs at 80–110 BPM (where beats are 550–750ms apart) previously suffered an artificial 150–250ms "dead zone" where pulse dropped to 0 before the next beat arrived. Now uses continuous exponential fade bridging seamlessly from beat to beat.
- **Continuous Audio Breath in Fluid Field**: Integrated continuous `audioBreath` (driven by live bass and mid frequencies) directly into metaball field radii in `FluidModel.field()`. Satellite droplets and lobes remain plump, visible, and alive at all times, preventing the fluid from collapsing into dormancy during quiet or syncopated passages.
- **Dynamic Groove Sensitivity**: Calibrated beat detection threshold from 42% down to 34% with tuned onset flux (`0.08 * dynamicRange`), ensuring soft groove hits, funk syncopations, and verse drum accents are captured reliably without false-triggering on bass guitar drones.

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
