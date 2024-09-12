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

    // 특정 매핑을 가져오는 메소드
    public static String getInstitutionDescription(String value) {
        return institutionMap.getOrDefault(value, "Unknown Institution");
    }

    public static String getGenderDescription(String value) {
        return genderMap.getOrDefault(value, "Unknown Gender");
    }
}

// 추후
