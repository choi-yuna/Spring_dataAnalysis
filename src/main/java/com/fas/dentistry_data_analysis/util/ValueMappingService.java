package com.fas.dentistry_data_analysis.util;

import java.util.HashMap;
import java.util.Map;

public class ValueMappingService {

    // 기관 ID 매핑
    private static final Map<String, String> institutionMap = new HashMap<>() {{
        put("1", "원광대학교");
        put("2", "고려대학교");
        put("3", "서울대학교");
        put("4", "국립암센터");
        put("5", "단국대");
        put("6", "조선대");
        put("7", "보라매병원");
    }};

    // 성별 매핑
    private static final Map<String, String> genderMap = new HashMap<>() {{
        put("1", "남자");
        put("2", "여자");
    }};

    // 흡연력 매핑
    private static final Map<String, String> smokingMap = new HashMap<>() {{
        put("1", "10개피/일 이상");
        put("2", "10개피/일 미만");
        put("3", "금연");
    }};

    // 음주 매핑
    private static final Map<String, String> alcoholMap = new HashMap<>() {{
        put("1", "유");
        put("2", "무");
    }};

    // 당뇨 매핑
    private static final Map<String, String> diabetesMap = new HashMap<>() {{
        put("1", "유");
        put("2", "무");
    }};

    // 심혈관 질환 매핑
    private static final Map<String, String> cardiovascularDiseaseMap = new HashMap<>() {{
        put("1", "유");
        put("2", "무");
    }};

    // 촬영 종류 매핑
    private static final Map<String, String> imageSourceMap = new HashMap<>() {{
        put("1", "파노라마");
        put("2", "CBCT");
        put("3", "MDCT");
    }};

    // 치주질환 여부 매핑
    private static final Map<String, String> perioDiseaseMap = new HashMap<>() {{
        put("1", "치주질환");
        put("2", "Others");
    }};

    // 특정 매핑을 가져오는 메소드
    public static String getInstitutionDescription(String value) {
        return institutionMap.getOrDefault(value, "Unknown Institution");
    }

    public static String getGenderDescription(String value) {
        return genderMap.getOrDefault(value, "Unknown Gender");
    }

    public static String getSmokingDescription(String value) {
        return smokingMap.getOrDefault(value, "Unknown Smoking Status");
    }

    public static String getAlcoholDescription(String value) {
        return alcoholMap.getOrDefault(value, "Unknown Alcohol Status");
    }

    public static String getDiabetesDescription(String value) {
        return diabetesMap.getOrDefault(value, "Unknown Diabetes Status");
    }

    public static String getCardiovascularDiseaseDescription(String value) {
        return cardiovascularDiseaseMap.getOrDefault(value, "Unknown Cardiovascular Disease Status");
    }

    public static String getImageSourceDescription(String value) {
        return imageSourceMap.getOrDefault(value, "Unknown Image Source");
    }

    public static String getPerioDiseaseDescription(String value) {
        return perioDiseaseMap.getOrDefault(value, "Unknown Periodontal Disease Status");
    }
}
