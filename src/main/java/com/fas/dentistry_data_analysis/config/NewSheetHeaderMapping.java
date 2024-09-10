package com.fas.dentistry_data_analysis.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewSheetHeaderMapping {
    private static final Map<String, List<String>> sheetHeaderMap = new HashMap<>();

    static {
        sheetHeaderMap.put("(치주질환) CRF", Arrays.asList(
                "DISEASE_CLASS","INSTITUTION_ID", "CAPTURE_TIME", "MAKER_INFO",
                // 환자 정보
                "P_GENDER", "P_AGE", "P_WEIGHT", "P_HEIGHT",
                // 질병력
                "MH_DIABETES", "MH_HIGHBLOOD", "MH_OSTEOPROSIS", "MH_NOTE",
                // 생활 습관
                "LS_SMOKE", "LS_ALCHOLE",
                // 진단정보별 환자수
                "DIA_PERIO",
                // 치아
                "Tooth_11", "Tooth_12", "Tooth_13", "Tooth_14", "Tooth_15", "Tooth_16", "Tooth_17", "Tooth_18",
                "Tooth_21", "Tooth_22", "Tooth_23", "Tooth_24", "Tooth_25", "Tooth_26", "Tooth_27", "Tooth_28",
                "Tooth_31", "Tooth_32", "Tooth_33", "Tooth_34", "Tooth_35", "Tooth_36", "Tooth_37", "Tooth_38",
                "Tooth_41", "Tooth_42", "Tooth_43", "Tooth_44", "Tooth_45", "Tooth_46", "Tooth_47", "Tooth_48"
        ));

        sheetHeaderMap.put("(골수염) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE",
                "DIA_PERIO","Tooth_11", "Tooth_12", "Tooth_13", "Tooth_14", "Tooth_15", "Tooth_16", "Tooth_17", "Tooth_18",
                "Tooth_21", "Tooth_22", "Tooth_23", "Tooth_24", "Tooth_25", "Tooth_26", "Tooth_27", "Tooth_28",
                "Tooth_31", "Tooth_32", "Tooth_33", "Tooth_34", "Tooth_35", "Tooth_36", "Tooth_37", "Tooth_38",
                "Tooth_41", "Tooth_42", "Tooth_43", "Tooth_44", "Tooth_45", "Tooth_46", "Tooth_47", "Tooth_48"
        ));

        sheetHeaderMap.put("(구강암) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_NAME","DI_LOC","PT_TNM","CAN_NUM","LYM_NUM"
        ));

        sheetHeaderMap.put("(두개안면기형) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_DISEASE","DI_NAME","DI_DETAIL"
        ));
    }

    public static List<String> getHeadersForSheet(String sheetName) {
        return sheetHeaderMap.get(sheetName);
    }
}
