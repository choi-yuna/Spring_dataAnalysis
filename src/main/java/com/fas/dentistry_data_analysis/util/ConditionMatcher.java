package com.fas.dentistry_data_analysis.util;

public class ConditionMatcher {

    // 나이 필터링 로직 (P_AGE)
    public static boolean matchesAgeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;
        }

        int age = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> age < 10;
            case "1" -> age >= 10 && age <= 20;
            case "2" -> age >= 21 && age <= 30;
            case "3" -> age >= 31 && age <= 40;
            case "4" -> age >= 41 && age <= 50;
            case "5" -> age >= 51 && age <= 60;
            case "6" -> age >= 61 && age <= 70;
            case "7" -> age >= 71 && age <= 80;
            case "8" -> age >= 81 && age <= 90;
            case "9" -> age > 90;
            default -> false;
        };
    }

    // 체중 필터링 로직 (P_WEIGHT)
    public static boolean matchesWeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;
        }

        int weight = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> weight < 40;
            case "1" -> weight >= 40 && weight <= 50;
            case "2" -> weight >= 51 && weight <= 60;
            case "3" -> weight >= 61 && weight <= 70;
            case "4" -> weight >= 71 && weight <= 80;
            case "5" -> weight >= 81 && weight <= 90;
            case "6" -> weight > 90;
            default -> false;
        };
    }

    // 키 필터링 로직 (P_HEIGHT)
    public static boolean matchesHeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;
        }

        int height = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> height < 140;
            case "1" -> height >= 141 && height <= 150;
            case "2" -> height >= 151 && height <= 160;
            case "3" -> height >= 161 && height <= 170;
            case "4" -> height >= 171 && height <= 180;
            case "5" -> height >= 181 && height <= 190;
            case "6" -> height > 190;
            default -> false;
        };
    }

    // 연도 범위 필터링 로직 (CAPTURE_TIME)
    public static boolean matchesYearRangeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;
        }

        int year;
        try {
            year = Integer.parseInt(actualValue);
        } catch (NumberFormatException e) {
            return false;
        }

        return switch (expectedSendValue) {
            case "12" -> year >= 1201 && year <= 1212;
            case "13" -> year >= 1301 && year <= 1312;
            case "14" -> year >= 1401 && year <= 1412;
            case "15" -> year >= 1501 && year <= 1512;
            case "16" -> year >= 1601 && year <= 1612;
            case "17" -> year >= 1701 && year <= 1712;
            case "18" -> year >= 1801 && year <= 1812;
            case "19" -> year >= 1901 && year <= 1912;
            case "20" -> year >= 2001 && year <= 2012;
            case "21" -> year >= 2101 && year <= 2112;
            case "22" -> year >= 2201 && year <= 2212;
            case "23" -> year >= 2301 && year <= 2312;
            default -> false;
        };
    }

}
