package com.fas.dentistry_data_analysis.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SheetHeaderMapping {

    private static final Map<String, List<String>> sheetHeaderMap = new HashMap<>();

    static {
        sheetHeaderMap.put("(치주질환) CRF", Arrays.asList(
                //필수
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                //선택
                "MAKER_INFO","H_RESOLUTION","V_RESOLUTION",
                //필수
                "IMAGE_ID", "P_GENDER", "P_AGE",
                //선택
                "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "MH_DIABETES","MH_HIGHBLOOD", "MH_OSTEOPROSIS", "MH_NOTE", "LS_SMOKE", "LS_ALCHOLE",
                //필수
                "DIA_PERIO",
                //선택
                "DIA_NOTE", "DIA_MISSTEETH_A", "DIA_MISSTEETH_B"
        ));

        sheetHeaderMap.put("(골수염) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                //선택항목
                "MAKER_INFO", "H_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO",
                //필수항목
                "IMAGE_ID", "P_GENDER", "P_AGE",
                //선택항목
                "P_WEIGHT","P_HEIGHT","P_RES_AREA",
                //필수항목
                "DIS_LOC","DIS_CLASS",
                "HTN","HLD","HLD","DIA","TAC","HD","TD","LD","KD","RA","CANCER","DEM","SMOK","STER","CHEMO","IMM_D",
                //필수
                "EXTRACTION", "TRAUMA", "IMPLANT", "BONE_SUR", "ORIGIN_INF",
                //선택항목
                "VAS_INSUF","LF_NOTE",
                //필수
                "FIRST_TREAT", "RECUR"
        ));

        sheetHeaderMap.put("(구강암) CRF", Arrays.asList(
                //필수
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                //선택
                "MAKER_INFO", "H_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO",
                //필수
                "IMAGE_ID", "P_GENDER", "P_AGE",
                //선택
                "P_WEIGHT","P_HEIGHT","P_RES_AREA","DH_SMOKE","DH_ALCHO","DH_DIAB","DH_CARDIO",
                //필수
                "DI_NAME","DI_LOC",
                //선택
                "DI_SUR","DI_RAD","DI_CAN","BT_WBC","BT_HB","BT_HCT","BT_OTPT","BT_GFR",
                //필수
                "PT_TNM",
                //선택
                "PT_DOI","PT_SIZE", "PT_NODE",	"PT_EI","PT_VI", "PT_BI","PT_LI"
        ));

        sheetHeaderMap.put("(두개안면기형) CRF", Arrays.asList(
                //필수
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                //선택
                "MAKER_INFO","H_RESOLUTION","V_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO",
                //필수
                "IMAGE_ID", "P_GENDER", "P_AGE",
                //선택
                "P_WEIGHT","P_HEIGHT","P_RES_AREA",
                //필수
                "DI_DISEASE","DI_TIME","MAKER_INFO",
                //선택항목
               "DI_NOTE","CI_SURGERY"
        ));
    }

    public static List<String> getHeadersForSheet(String sheetName) {
        return sheetHeaderMap.get(sheetName);
    }
}
