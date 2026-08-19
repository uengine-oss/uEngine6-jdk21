package org.uengine.kernel.bpmn.sql;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * How a {@link DatabaseMappingStrategy} turns a mapping table into SQL.
 *
 * <p>
 * The legacy {@code DatabaseMappingActivity} used bare int constants
 * (1=SELECT, 2=INSERT, 3=UPDATE, 4=DELETE); {@link #forLegacyCode(int)} keeps
 * definitions authored back then readable.
 * </p>
 */
public enum QueryMode {

    SELECT(1),
    INSERT(2),
    UPDATE(3),
    DELETE(4);

    private final int legacyCode;

    QueryMode(int legacyCode) {
        this.legacyCode = legacyCode;
    }

    public int getLegacyCode() {
        return legacyCode;
    }

    /**
     * Reads either the enum name ("INSERT") or a legacy int code (2), so that
     * definitions authored against {@code DatabaseMappingActivity} still load.
     */
    @JsonCreator
    public static QueryMode fromJson(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return forLegacyCode(((Number) value).intValue());
        }

        String text = value.toString().trim();

        try {
            return valueOf(text.toUpperCase());
        } catch (IllegalArgumentException notAName) {
            try {
                return forLegacyCode(Integer.parseInt(text));
            } catch (NumberFormatException notACode) {
                throw notAName;
            }
        }
    }

    public static QueryMode forLegacyCode(int legacyCode) {
        for (QueryMode mode : values()) {
            if (mode.legacyCode == legacyCode) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Unknown legacy query mode code: " + legacyCode);
    }
}
