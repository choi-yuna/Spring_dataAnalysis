package com.fas.dentistry_data_analysis.dataAnlaysis.util.json;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonHeaderMapping {

    private static final Map<String, Map<String, List<String>>> jsonHeaderMap = new HashMap<>();

    static {
        jsonHeaderMap.put("A", Map.of(
                "required", Arrays.asList(
                        "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                        "Identifier", "P_GENDER", "P_AGE", "DIA_PERIO","11","12","13","14","15","16","17","18","21","22","23","24","25","26","27","28","31","32","33","34","35","36","37","38","41","42","43","44","45","46","47","48"
                ),
                "optional", Arrays.asList(
                        "MAKER_IF", "H_RESOLUTION", "V_RESOLUTION",
                        "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "MH_DIABETES", "MH_HIGHBLOOD", "MH_OSTEOPROSIS",
                        "MH_NOTE", "LS_SMOKE", "LS_ALCHOLE", "DIA_NOTE", "DIA_MISSTEETH_A", "DIA_MISSTEETH_B"
                )
        ));

        jsonHeaderMap.put("B", Map.of(
                "required", Arrays.asList(
                        "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                        "IMAGE_ID", "P_GENDER", "P_AGE", "DIS_LOC", "DIS_CLASS",
                        "EXTRACTION", "TRAUMA", "IMPLANT", "BONE_SUR", "ORIGIN_INF",
                        "FIRST_TREAT", "RECUR","OST_NUM"
                ),
                "optional", Arrays.asList(
                        "MAKER_IF", "H_RESOLUTION", "V_RESOLUTION", "TOTAL_SLICE_NO",
                        "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "VAS_INSUF", "LF_NOTE",
                        "HTN", "HLD", "DIA", "TAC", "HD", "TD", "LD", "KD", "RA", "CANCER",
                        "DEM", "SMOK", "STER", "CHEMO", "IMM_D"
                )
        ));

        jsonHeaderMap.put("C", Map.of(
                "required", Arrays.asList(
                        "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                        "IMAGE_ID", "P_GENDER", "P_AGE", "DI_NAME", "DI_LOC", "PT_TNM","CAN_NUM","LYM_NUM"
                ),
                "optional", Arrays.asList(
                        "MAKER_INFO", "H_RESOLUTION", "V_RESOLUTION", "TOTAL_SLICE_NO",
                        "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "DH_SMOKE", "DH_ALCHO", "DH_DIAB", "DH_CARDIO",
                        "DI_SUR", "DI_RAD", "DI_CAN", "BT_WBC", "BT_HB", "BT_HCT", "BT_OTPT", "BT_GFR",
                        "PT_DOI", "PT_SIZE", "PT_NODE", "PT_EI", "PT_VI", "PT_BI", "PT_LI"
                )
        ));

        jsonHeaderMap.put("D", Map.of(
                "required", Arrays.asList(
                        "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                        "IMAGE_ID", "P_GENDER", "P_AGE", "DI_DISEASE", "DI_TIME","DI_DETAIL"
                ),
                "optional", Arrays.asList(
                        "MAKER_IF","RESOLUTION_H","RESOLUTION_V","SLICETHICKNESS","TOTAL_SLICE_NO",
                        "P_WEIGHT", "P_HEIGHT", "P_RES_AREA", "DI_NOTE", "CI_SURGERY"
                )
        ));
    }

    public static Map<String, List<String>> getHeadersForJson(String jsonName) {
        return jsonHeaderMap.get(jsonName);
    }
}


