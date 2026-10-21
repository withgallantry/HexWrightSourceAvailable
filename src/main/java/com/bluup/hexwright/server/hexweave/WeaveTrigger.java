package com.bluup.hexwright.server.hexweave;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public record WeaveTrigger(int id, Kind kind, String key, int reach, List<Integer> targets) {

    public static final int MAX_KEY_LENGTH = 64;
    public static final int MIN_REACH = 1;
    public static final int MAX_REACH = 5;
    public static final int DEFAULT_REACH = 1;

    public enum Kind {
        ON_USE,
        SNEAK_USE,
        KEY,
        KEY_USE,
        PROXIMITY,
        PROJECTILE,
        TARGETED,
        GAZE,
        PARRY,
        FALLING;

        private final String id = name().toLowerCase(Locale.ROOT);

        public String id() {
            return id;
        }

        public boolean keyed() {
            return this == KEY || this == KEY_USE;
        }

        public boolean ranged() {
            return this == PROXIMITY;
        }

        public int defaultReach() {
            return ranged() ? DEFAULT_REACH : 0;
        }

        public static @Nullable Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public WeaveTrigger {
        targets = List.copyOf(targets);
    }

    public WeaveTrigger(int id, Kind kind, String key, List<Integer> targets) {
        this(id, kind, key, kind.defaultReach(), targets);
    }

    public WeaveTrigger(int id, Kind kind, List<Integer> targets) {
        this(id, kind, "", targets);
    }
}
