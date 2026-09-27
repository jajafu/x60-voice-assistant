package android.os;

/** JVM-only clock for diagnostics in orchestrator tests; no Android/native behavior is simulated. */
public final class SystemClock {
    public static long elapsedRealtime() { return System.nanoTime() / 1_000_000; }
}
