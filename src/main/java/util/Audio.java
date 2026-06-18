package util;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/**
 * The game's sound engine — the {@code caster_*} stand-in (architecture: util/Audio).
 * Two roles, mirroring how Undertale uses audio:
 *
 * <ul>
 *   <li><b>Music</b> ({@code mus_*}) — one looping track at a time. {@link #loop}
 *       stops the previous track and starts the new one; {@link #stopMusic} ends it.
 *       This is the single "music bus": only one boss theme ever plays.</li>
 *   <li><b>SFX</b> ({@code snd_*}) — fire-and-forget one-shots via {@link #play}.
 *       Many can overlap (menu blips, hits); each gets its own short-lived line that
 *       closes itself when it finishes.</li>
 * </ul>
 *
 * <p><b>OGG support.</b> The stock JVM cannot decode Vorbis, so every {@code .ogg}
 * was silent. With the vorbisspi/jorbis SPI on the classpath (see {@code libs/}),
 * {@link #toPcm} converts any decoded stream to signed PCM before handing it to a
 * {@link Clip}, so both {@code .wav} and {@code .ogg} now play.
 *
 * <p><b>Failure is always silent.</b> A missing file, an undecodable format, or no
 * available mixer yields no sound rather than an exception — the fight still runs.
 * The engine also mutes itself in a headless JVM (renders/tests) or when the
 * {@code undertale.muteAudio} system property is set.
 *
 * // GML: caster_load / caster_loop / caster_play_sfx / caster_set_volume
 */
public final class Audio {

    private Audio() {
    }

    // ---- Common SFX paths (referenced across the engine) --------------------
    /** Menu cursor move (FIGHT/ACT navigation, boss-select rows). */
    public static final String SFX_MOVE = "/audio/snd_squeak.wav";
    /** Menu confirm / button press. */
    public static final String SFX_SELECT = "/audio/snd_select.wav";
    /** The player SOUL takes a hit. */
    public static final String SFX_HURT = "/audio/snd_hurt1.wav";
    /** A FIGHT strike lands on the monster. */
    public static final String SFX_DAMAGE = "/audio/snd_damage.wav";

    /** Master mute. Off in a headless JVM (renders/tests) or via system property. */
    private static volatile boolean enabled =
            !Boolean.getBoolean("undertale.muteAudio")
            && !java.awt.GraphicsEnvironment.isHeadless();

    /** Global scale applied to every clip's volume (0..1). */
    private static volatile double masterVolume = 0.7;

    /** User mute toggle (M key) — silences output without tearing the music down. */
    private static volatile boolean muted = false;

    /** The single active music track (the "music bus"). */
    private static volatile Clip music;

    /** Decoded-PCM cache so a repeated one-shot doesn't re-decode each time. */
    private static final Map<String, Sound> SFX_CACHE = new HashMap<>();

    /** A decoded sound effect held in memory: its PCM format and raw bytes. */
    private record Sound(AudioFormat format, byte[] pcm) {
    }

    // ---- Master controls ----------------------------------------------------

    /** Enable/disable all playback (the game enables it; tests/renders leave it off). */
    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            stopMusic();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Set the global volume scale (0 silent … 1 full); re-applies to live music. */
    public static void setMasterVolume(double volume) {
        masterVolume = clamp01(volume);
        setVolume(music, 1.0);
    }

    /**
     * Toggle mute (the M key). Unlike {@link #setEnabled}, this keeps the music
     * track running (silently) so un-muting picks the song back up where it is.
     * Returns the new muted state.
     */
    public static boolean toggleMute() {
        muted = !muted;
        setVolume(music, 1.0);   // re-apply gain to the live track
        return muted;
    }

    public static boolean isMuted() {
        return muted;
    }

    // ---- Music (single looping channel) -------------------------------------

    /**
     * Loop a track forever, replacing any current music. Returns the {@link Clip}
     * (so callers can ramp its volume, e.g. Sans's intro), or {@code null} if it
     * can't be loaded/decoded. GML: caster_loop.
     */
    public static Clip loop(String resourcePath) {
        stopMusic();
        if (!enabled) {
            return null;
        }
        try (InputStream in = Audio.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            AudioInputStream pcm = toPcm(AudioSystem.getAudioInputStream(new BufferedInputStream(in)));
            Clip clip = AudioSystem.getClip();
            clip.open(pcm);
            music = clip;
            setVolume(clip, 1.0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            return clip;
        } catch (Exception e) {
            return null;
        }
    }

    /** Stop the current music track. Null-safe. GML: caster_stop on the song. */
    public static void stopMusic() {
        stop(music);
    }

    // ---- One-shot SFX -------------------------------------------------------

    /** Play a sound effect once at default volume. GML: caster_play. */
    public static void play(String resourcePath) {
        play(resourcePath, 1.0, 1.0);
    }

    /** Play a sound effect once at the given volume (0..1). */
    public static void play(String resourcePath, double volume) {
        play(resourcePath, volume, 1.0);
    }

    /**
     * Play a sound effect once. {@code pitch} scales the sample rate (1.0 normal,
     * 2.0 an octave up, 0.5 an octave down) — GML's {@code caster_set_pitch}.
     * The line closes itself once the clip finishes, so shots never leak lines.
     */
    public static void play(String resourcePath, double volume, double pitch) {
        if (!enabled || muted) {
            return;
        }
        Sound sfx = decode(resourcePath);
        if (sfx == null) {
            return;
        }
        try {
            AudioFormat fmt = sfx.format();
            if (pitch != 1.0) {
                fmt = new AudioFormat(fmt.getEncoding(), (float) (fmt.getSampleRate() * pitch),
                        fmt.getSampleSizeInBits(), fmt.getChannels(), fmt.getFrameSize(),
                        (float) (fmt.getFrameRate() * pitch), fmt.isBigEndian());
            }
            Clip clip = AudioSystem.getClip();
            clip.open(fmt, sfx.pcm(), 0, sfx.pcm().length);
            setVolume(clip, volume);
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    ev.getLine().close();
                }
            });
            clip.start();
        } catch (Exception e) {
            // Device busy / no free line — drop this shot silently.
        }
    }

    // ---- Shared helpers -----------------------------------------------------

    /** GML: caster_set_volume — set a clip's gain (0..1), scaled by master. Null-safe. */
    public static void setVolume(Clip clip, double volume) {
        if (clip == null) {
            return;
        }
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            if (muted) {
                gain.setValue(gain.getMinimum());   // fully silent, but keep the line alive
                return;
            }
            double v = clamp01(volume) * masterVolume;
            double db = 20.0 * Math.log10(Math.max(0.0001, v));
            gain.setValue((float) Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (Exception e) {
            // No gain control on this mixer — play at its default level.
        }
    }

    /** Stop and release a clip; clears the music bus if it was the music track. Null-safe. */
    public static void stop(Clip clip) {
        if (clip == null) {
            return;
        }
        if (clip == music) {
            music = null;
        }
        try {
            clip.stop();
            clip.close();
        } catch (Exception e) {
            // Already closed — nothing to do.
        }
    }

    /** Decode (once) a resource to in-memory PCM. Caches both hits and misses. */
    private static Sound decode(String resourcePath) {
        if (SFX_CACHE.containsKey(resourcePath)) {
            return SFX_CACHE.get(resourcePath);
        }
        Sound sfx = null;
        try (InputStream in = Audio.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                AudioInputStream pcm = toPcm(AudioSystem.getAudioInputStream(new BufferedInputStream(in)));
                sfx = new Sound(pcm.getFormat(), pcm.readAllBytes());
            }
        } catch (Exception e) {
            sfx = null; // undecodable — cache the miss so we don't retry every shot
        }
        SFX_CACHE.put(resourcePath, sfx);
        return sfx;
    }

    /** Convert any decoded stream (OGG/etc.) to signed 16-bit PCM a Clip can open. */
    private static AudioInputStream toPcm(AudioInputStream src) {
        AudioFormat base = src.getFormat();
        if (base.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            return src;
        }
        AudioFormat pcm = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16, base.getChannels(),
                base.getChannels() * 2, base.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(pcm, src);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
