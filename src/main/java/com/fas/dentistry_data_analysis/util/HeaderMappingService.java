package com.fas.dentistry_data_analysis.util;

import java.util.Arrays;
import java.util.List;

public class HeaderMappingService {

    // 동적으로 title을 결정하는 메소드
    public static String determineTitleBasedOnHeaders(List<String> headers) {
        if (headers.contains("INSTITUTION_ID")) {
            return "기관별 데이터";
        } else if (headers.contains("P_AGE")) {
            return "나이별 데이터";
        } else if (headers.contains("P_WEIGHT")) {
            return "체중별 데이터";
        } else if (headers.contains("P_HEIGHT")) {
            return "키별 데이터";
        } else {
            return "기타 데이터";
        }
    }

    // 동적으로 headers를 결정하는 메소드
    public static List<String> determineHeadersBasedOnFilters(List<String> headers) {
        if (headers.contains("INSTITUTION_ID")) {
            return Arrays.asList("기관 ID", "환자수");
        } else if (headers.contains("P_AGE")) {
            return Arrays.asList("나이", "환자수");
        } else if (headers.contains("P_WEIGHT")) {
            return Arrays.asList("체중", "환자수");
        } else if (headers.contains("P_HEIGHT")) {
            return Arrays.asList("키", "환자수");
        } else {
            return Arrays.asList("기타 정보", "수량");
        }
    }
}
