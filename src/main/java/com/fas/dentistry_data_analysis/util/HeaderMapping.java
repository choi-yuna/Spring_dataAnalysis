package com.fas.dentistry_data_analysis.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeaderMapping {

    // 미리 정의된 헤더 매핑을 위한 Map 생성
    private static final Map<String, List<String>> headerMapping;
    private static final Map<String, String> titleMapping; // Title 대한 매핑 추가

    static {
        // header와 대응하는 값들을 미리 Map에 매핑
        headerMapping = new HashMap<>();
        headerMapping.put("INSTITUTION_ID", Arrays.asList("기관명", "환자수"));
        headerMapping.put("P_AGE", Arrays.asList("나이", "환자수"));
        headerMapping.put("P_WEIGHT", Arrays.asList("체중", "환자수"));
        headerMapping.put("P_HEIGHT", Arrays.asList("키", "환자수"));
        headerMapping.put("P_GENDER", Arrays.asList("성별", "환자수"));
        headerMapping.put("CAPTURE_TIME", Arrays.asList("촬영일자", "환자수"));
        headerMapping.put("LS_SMOKE", Arrays.asList("흡연력", "환자수"));
        headerMapping.put("LS_ALCHOLE", Arrays.asList("음주", "환자수"));
        headerMapping.put("MH_DIABETES", Arrays.asList("당뇨", "환자수"));
        headerMapping.put("MH_HIGHBLOOD", Arrays.asList("고혈압", "환자수"));
        headerMapping.put("MH_OSTEOPROSIS", Arrays.asList("골다공증", "환자수"));
        headerMapping.put("CARDIOVASCULAR_DISEASE", Arrays.asList("심혈관 질환", "환자수"));
        headerMapping.put("DIA_PERIO", Arrays.asList("치주질환여부", "환자수"));
        headerMapping.put("DIA_NOTE", Arrays.asList("진단소견", "환자수"));
        headerMapping.put("DIA_MISSTEETH_A", Arrays.asList("결손치 수", "환자수"));
        headerMapping.put("DIA_MISSTEETH_B", Arrays.asList("치주질환 원인이 아닌 결손치 수", "환자수"));
        headerMapping.put("DIA_MISSTEETH_C", Arrays.asList("치주염으로 인한 결손치 수", "환자수"));
        headerMapping.put("DIS_LOC", Arrays.asList("발생부위", "환자수"));
        headerMapping.put("DIS_CLASS", Arrays.asList("골수염 종류", "환자수"));
        headerMapping.put("MR_STAGE", Arrays.asList("Stage", "환자수"));
        headerMapping.put("MR_HOWTOTAKE", Arrays.asList("약물복용방법", "환자수"));
        headerMapping.put("MR_HOWLONG", Arrays.asList("약물복용기간", "환자수"));
        headerMapping.put("EXTRACTION", Arrays.asList("Extraction", "환자수"));
        headerMapping.put("TRAUMA", Arrays.asList("Trauma", "환자수"));
        headerMapping.put("IMPLANT", Arrays.asList("Implant", "환자수"));
        headerMapping.put("BONE_SUR", Arrays.asList("Bone sugery", "환자수"));
        headerMapping.put("ORIGIN_INF", Arrays.asList("Dental origin infection", "환자수"));
        headerMapping.put("FIRST_TREAT", Arrays.asList("처음 처치법", "환자수"));
        headerMapping.put("RECUR", Arrays.asList("Recurrence", "환자수"));
        headerMapping.put("DI_DISEASE", Arrays.asList("두개안면 기형 여부", "환자수"));
        headerMapping.put("DI_TIME", Arrays.asList("데이터 획득시기", "환자수"));
        headerMapping.put("DI_DETAIL", Arrays.asList("세부진단", "환자수"));
        headerMapping.put("DI_NAME", Arrays.asList("진단명", "환자수"));
        headerMapping.put("DI_LOC", Arrays.asList("병소부위", "환자수"));
        headerMapping.put("PT_TNM", Arrays.asList("TNM stage", "환자수"));
        headerMapping.put("MAKER_INFO", Arrays.asList("촬영 장비","환자수"));
        headerMapping.put("IMAGE_SRC", Arrays.asList("촬영 종류","환자수"));
        headerMapping.put("P_RES_AREA", Arrays.asList("주거지역","환자수"));
        headerMapping.put("CAN_NUM", Arrays.asList("구강암 개수","환자수"));
        headerMapping.put("LYM_NUM", Arrays.asList("임파절 전이 개수","환자수"));
        headerMapping.put("OST_NUM", Arrays.asList("골수염 개수","환자수"));

        // title과 대응하는 값들을 미리 Map에 매핑
        titleMapping = new HashMap<>();
        titleMapping.put("MAKER_INFO", " 촬영장비 데이터");
        titleMapping.put("INSTITUTION_ID", "기관별 데이터");
        titleMapping.put("P_AGE", "나이별 데이터");
        titleMapping.put("P_WEIGHT", "체중별 데이터");
        titleMapping.put("P_HEIGHT", "키별 데이터");
        titleMapping.put("P_GENDER", "성별 데이터");
        titleMapping.put("CAPTURE_TIME", "촬영일자별 데이터");
        titleMapping.put("LS_SMOKE", "흡연력 데이터");
        titleMapping.put("LS_ALCHOLE", "음주 데이터");
        titleMapping.put("MH_DIABETES", "당뇨 데이터");
        titleMapping.put("MH_HIGHBLOOD", "고혈압 데이터");
        titleMapping.put("MH_OSTEOPROSIS", "골다공증 데이터");
        titleMapping.put("CARDIOVASCULAR_DISEASE", "심혈관 질환 데이터");
        titleMapping.put("DIA_PERIO", "치주질환 데이터");
        titleMapping.put("DIA_NOTE", "진단소견 데이터");
        titleMapping.put("DIA_MISSTEETH_A", "결손치 수 데이터");
        titleMapping.put("DIA_MISSTEETH_B", "치주질환 원인이 아닌 결손치 데이터");
        titleMapping.put("DIA_MISSTEETH_C", "치주염으로 인한 결손치 데이터");
        titleMapping.put("DIS_LOC", "발생부위 데이터");
        titleMapping.put("DIS_CLASS", "골수염 종류 데이터");
        titleMapping.put("MR_STAGE", "Stage 데이터");
        titleMapping.put("MR_HOWTOTAKE", "약물복용방법 데이터");
        titleMapping.put("MR_HOWLONG", "약물복용기간 데이터");
        titleMapping.put("EXTRACTION", "Extraction 데이터");
        titleMapping.put("TRAUMA", "Trauma 데이터");
        titleMapping.put("IMPLANT", "Implant 데이터");
        titleMapping.put("BONE_SUR", " Bone surgery 데이터");
        titleMapping.put("ORIGIN_INF", "Dental origin infection 데이터");
        titleMapping.put("FIRST_TREAT", "처음 처치법 데이터");
        titleMapping.put("RECUR", "Recurrence 데이터");
        titleMapping.put("DI_DISEASE", "두개안면 기형 여부 데이터");
        titleMapping.put("DI_TIME", "데이터 획득시기 데이터");
        titleMapping.put("DI_DETAIL", "세부진단 데이터");
        titleMapping.put("DI_NAME", "진단명 데이터");
        titleMapping.put("DI_LOC", "병소부위 데이터");
        titleMapping.put("PT_TNM", "TNM stage 데이터");
        titleMapping.put("IMAGE_SRC", "촬영종류 데이터");
        titleMapping.put("P_RES_AREA", "주거지역 데이터");
        titleMapping.put("CAN_NUM", "구강암 개수 데이터");
        titleMapping.put("LYM_NUM", "임파절 전이 개수 데이터");
        titleMapping.put("OST_NUM", "골수염 개수 데이터");
    }

    // Map 활용하여 동적으로 헤더를 가져오는 메소드
    public static List<String> determineHeadersBasedOnFilters(List<String> headers) {
        for (String header : headers) {
            // 해당 header Map에 있으면 반환, 없으면 "기타 정보" 반환
            if (headerMapping.containsKey(header)) {
                return headerMapping.get(header);
            }
        }
        // 해당하는 header 없을 경우 기본 값 반환
        return Arrays.asList("기타 정보", "환자 수");
    }

    // Map 활용하여 동적으로 title을 가져오는 메소드
    public static String determineTitleBasedOnHeaders(List<String> headers) {
        for (String header : headers) {
            // 해당 header Map 있으면 반환, 없으면 "기타 데이터" 반환
            if (titleMapping.containsKey(header)) {
                return titleMapping.get(header);
            }
        }
        // 해당하는 header 없을 경우 기본 값 반환
        return "기타 데이터";
    }
}
