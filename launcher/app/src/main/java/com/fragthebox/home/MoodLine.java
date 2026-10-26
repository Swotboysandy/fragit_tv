package com.fragthebox.home;

import java.util.Calendar;

/**
 * The serif line above the row: a short, calm sentence for the time of day. One is picked per day and part
 * of the day, so it stays put while you use the TV and changes when the morning becomes the afternoon.
 */
final class MoodLine {

    private static final String[] MORNING = {
            "Slow start. The day can wait a minute.",
            "Tea first, then everything else.",
            "Good light this morning.",
            "Open the curtains, then the apps.",
    };
    private static final String[] AFTERNOON = {
            "Halfway there. Take a breather.",
            "A quiet hour is still an hour.",
            "Something good is on somewhere.",
            "Feet up, just for one episode.",
    };
    private static final String[] EVENING = {
            "Lights low, sound up.",
            "The day is done. Pick something good.",
            "Dinner, then a story.",
            "Evening, at last.",
    };
    private static final String[] NIGHT = {
            "One more, then bed.",
            "The house is quiet now.",
            "Late again. Make it a good one.",
            "Night owl hours.",
    };

    private MoodLine() {
    }

    static String now() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String[] set = h >= 5 && h < 12 ? MORNING : h >= 12 && h < 17 ? AFTERNOON : h >= 17 && h < 22 ? EVENING : NIGHT;
        return set[c.get(Calendar.DAY_OF_YEAR) % set.length];
    }
}
