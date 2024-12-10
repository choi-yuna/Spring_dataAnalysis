package com.fas.dentistry_data_analysis.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SheetHeaderMapping {

    private static final Map<String, List<String>> sheetHeaderMap = new HashMap<>();

    static {
        sheetHeaderMap.put("(치주질환) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME", "IMAGE_ID",
                "P_GENDER", "P_AGE", "DIA_PERIO"
        ));

        sheetHeaderMap.put("(골수염) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE",
                "DIS_LOC","DIS_CLASS", "EXTRACTION", "TRAUMA", "IMPLANT", "BONE_SUR", "ORIGIN_INF", "FIRST_TREAT", "RECUR"
        ));

        sheetHeaderMap.put("(구강암) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_NAME","DI_LOC","PT_TNM"
        ));

        sheetHeaderMap.put("(두개안면기형) CRF", Arrays.asList(
                "DISEASE_CLASS", "INSTITUTION_ID", "PATIENT_NO", "IMAGE_NO", "IMAGE_SRC", "CAPTURE_TIME",
                "IMAGE_ID", "P_GENDER", "P_AGE","DI_DISEASE","DI_TIME","DI_DETAIL"
        ));
    }

    public static List<String> getHeadersForSheet(String sheetName) {
        return sheetHeaderMap.get(sheetName);
    }
}
