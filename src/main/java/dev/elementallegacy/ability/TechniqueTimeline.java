package dev.elementallegacy.ability;

/**
 * Shared client/server timing for every bending technique.
 *
 * The renderer and articulated player pose must use the same lifetime.
 * Values are expressed in Minecraft ticks (20 ticks per second).
 */
public final class TechniqueTimeline {
    private TechniqueTimeline() {}

    // Shared server/client progression for the advancing Seismic Wave front.
    public static final int SEISMIC_WAVE_START_TICK = 42;
    public static final int SEISMIC_WAVE_STEP_INTERVAL_TICKS = 3;
    public static final int SEISMIC_WAVE_STEP_COUNT = 9;
    public static final int SEISMIC_WAVE_BLOCK_LIFETIME_TICKS = 40;
    public static final double SEISMIC_WAVE_STEP_DISTANCE = 0.92D;

    public static int durationTicks(String abilityId) {
        if (abilityId == null) return 48;
        return switch (abilityId) {
            // Stone timelines are deliberately long enough for preparation,
            // material formation, release/impact and recovery.
            case "rock_throw" -> 90;       // 4.5 s
            case "stone_wall" -> 110;      // 5.5 s
            case "rock_cage" -> 130;       // 6.5 s
            case "seismic_wave" -> 160;    // 8.0 s
            case "stone_armor" -> 230;     // 11.5 s including active guard
            case "earthquake" -> 240;      // 12.0 s

            // Existing non-Stone durations retained until their own redesign.
            case "ice_bind" -> 140;
            case "water_shield", "air_shield" -> 120;
            default -> 48;
        };
    }
}
