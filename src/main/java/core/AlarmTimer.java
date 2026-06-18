package core;

/**
 * Emulates GML's per-instance {@code alarm[0..11]} countdown timers. Each frame
 * every active alarm decrements; when one reaches 0 it fires the owner's
 * {@code onAlarm(n)} event and switches itself off.
 *
 * <p>GML convention: {@code alarm[n] == -1} means "off". Setting an alarm to a
 * frame count starts the countdown.
 *
 * // GML: alarm[0..11] + Alarm events
 */
public final class AlarmTimer {

    public static final int COUNT = 12;
    private static final int OFF = -1;

    private final Entity owner;
    private final int[] alarm = new int[COUNT];

    public AlarmTimer(Entity owner) {
        this.owner = owner;
        for (int i = 0; i < COUNT; i++) {
            alarm[i] = OFF;
        }
    }

    /** GML: alarm[n] = frames. */
    public void set(int n, int frames) {
        alarm[n] = frames;
    }

    /** GML: alarm[n]. */
    public int get(int n) {
        return alarm[n];
    }

    /** GML: alarm[n] = -1. */
    public void cancel(int n) {
        alarm[n] = OFF;
    }

    public boolean isActive(int n) {
        return alarm[n] >= 0;
    }

    /**
     * GML alarm semantics: an alarm at 0 fires this frame, an alarm at &gt;0
     * decrements. Matches GameMaker, where the Alarm event runs the step the
     * counter hits 0.
     */
    public void tick() {
        for (int n = 0; n < COUNT; n++) {
            if (alarm[n] == 0) {
                alarm[n] = OFF;
                owner.onAlarm(n);
            } else if (alarm[n] > 0) {
                alarm[n]--;
                if (alarm[n] == 0) {
                    alarm[n] = OFF;
                    owner.onAlarm(n);
                }
            }
        }
    }
}
