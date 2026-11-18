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
    // 9 steps at 5-tick intervals travel 7.02 blocks from 2.0s to 4.0s;
    // the last temporary ridge settles/restores at 6.0s (40 ticks later).
    public static final int SEISMIC_WAVE_START_TICK = 40;
    public static final int SEISMIC_WAVE_STEP_INTERVAL_TICKS = 5;
    public static final int SEISMIC_WAVE_STEP_COUNT = 9;
    public static final int SEISMIC_WAVE_BLOCK_LIFETIME_TICKS = 40;
    public static final double SEISMIC_WAVE_STEP_DISTANCE = 0.78D;

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
