package com.fas.dentistry_data_analysis.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SheetHeaderMapping {

    private static final Map<String, List<String>> sheetHeaderMap = new HashMap<>();

    static {
        sheetHeaderMap.put("(치주질환) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME", "MAKER_INFO","H_RESOLUTION","V_RESOLUTION","IMAGE_ID",
                "P_GENDER", "P_AGE", "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "MH_DIABETES","P_RES_AREA","MH_HIGHBLOOD", "MH_OSTEOPROSIS", "MH_NOTE", "LS_SMOKE", "LS_ALCHOLE","DIA_PERIO", "DIA_NOTE", "DIA_MISSTEETH_A", "DIA_MISSTEETH_B"
        ));

        sheetHeaderMap.put("(골수염) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE",
                "DIS_LOC","DIS_CLASS", "EXTRACTION", "TRAUMA", "IMPLANT", "BONE_SUR", "ORIGIN_INF", "FIRST_TREAT", "RECUR",
                //선택항목
                "MAKER_INFO", "H_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO","P_WEIGHT","P_HEIGHT","P_RES_AREA","HTN","HLD","HLD","DIA","TAC","HD","TD","LD","KD","RA","CANCER","DEM","SMOK","STER","CHEMO","IMM_D","VAS_INSUF","LF_NOTE"
        ));

        sheetHeaderMap.put("(구강암) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_NAME","DI_LOC","PT_TNM",
                //선택항목
                "H_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO","P_WEIGHT","P_HEIGHT","P_RES_AREA","DH_SMOKE","DH_ALCHO","DH_DIAB","DH_CARDIO","DI_SUR","DI_RAD","DI_CAN","BT_WBC","BT_HB","BT_HCT","BT_OTPT","BT_GFR","PT_DOI",	"PT_SIZE",
                "PT_NODE",	"PT_EI","PT_VI", "PT_BI","PT_LI"

        ));

        sheetHeaderMap.put("(두개안면기형) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_DISEASE","DI_TIME","MAKER_INFO",
                //선택항목
               " MAKER_INFO","H_RESOLUTION","V_RESOLUTION","V_RESOLUTION","TOTAL_SLICE_NO","P_WEIGHT","P_HEIGHT","P_RES_AREA","DI_NOTE","CI_SURGERY"


        ));
    }

    public static List<String> getHeadersForSheet(String sheetName) {
        return sheetHeaderMap.get(sheetName);
    }
}
