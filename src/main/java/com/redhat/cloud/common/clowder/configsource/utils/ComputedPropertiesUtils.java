package com.redhat.cloud.common.clowder.configsource.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utilities to find computed properties. Computed properties are properties that depend on others. For example:
 *
 * <code>
 * other.property=value-a
 * computed.property=${other.property:value-b}
 * </code>
 *
 * In the above example, the "computed-property" is resolved with either "other.property" if exists or "value-b"
 * if it does not.
 */
public final class ComputedPropertiesUtils {

    public static final String PROPERTY_START = "${";
    public static final String PROPERTY_END = "}";

    private ComputedPropertiesUtils() {

    }

    public static boolean hasComputedProperties(String rawValue) {
        return isNotNullOrEmpty(rawValue) && rawValue.contains(PROPERTY_START);
    }

    public static String getPropertyFromSystem(String propertyName, String defaultValue) {
        String value = Optional.ofNullable(System.getProperty(propertyName))
                .orElseGet(() -> System.getenv(propertyName));

        return isNullOrEmpty(value) ? defaultValue : value;
    }

    public static List<String> getComputedProperties(String str) {
        if (isNullOrEmpty(str)) {
            return Collections.emptyList();
        }

        int closeLen = PROPERTY_END.length();
        int openLen = PROPERTY_START.length();
        List<String> list = new ArrayList<>();
        int end;
        for (int pos = 0; pos < str.length() - closeLen; pos = end + closeLen) {
            int start = str.indexOf(PROPERTY_START, pos);
            if (start < 0) {
                break;
            }

            start += openLen;
            end = str.indexOf(PROPERTY_END, start);
            if (end < 0) {
                break;
            }

            String currentStr = str.substring(start);
            String tentative = currentStr.substring(0, end - start);
            while (countMatches(tentative, PROPERTY_START) != countMatches(tentative, PROPERTY_END)) {
                end++;
                if (end >= str.length()) {
                    break;
                }

                tentative = currentStr.substring(0, end - start);
            }

            list.add(tentative);
        }

        return list;
    }

    private static int countMatches(String str, String sub) {
        if (!isNullOrEmpty(str) && !isNullOrEmpty(sub)) {
            int count = 0;

            for (int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sub.length()) {
                ++count;
            }

            return count;
        } else {
            return 0;
        }
    }

    private static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
