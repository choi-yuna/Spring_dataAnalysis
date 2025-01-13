package com.fas.dentistry_data_analysis.dataAnlaysis.util.excel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ValueMapping {

    // 각 필드별 매핑 로직을 함수로 정의하고 Map에 등록
    public static final Map<String, Function<String, String>> headerMappingFunctions = new HashMap<>() {{
        put("P_AGE", ValueMapping::getAgeRange);
        put("P_WEIGHT", ValueMapping::getWeightRange);
        put("P_HEIGHT", ValueMapping::getHeightRange);
        put("CAPTURE_TIME", ValueMapping::getYearRange);
        put("INSTITUTION_ID", ValueMapping::getInstitutionDescription);
        put("DISEASE_CLASS", ValueMapping::getDiseaseClass);
        put("P_GENDER", ValueMapping::getGenderDescription);
        put("LS_SMOKE", ValueMapping::getSmokingDescription);
        put("LS_ALCHOLE", ValueMapping::getAlcoholDescription);
        put("MH_DIABETES", ValueMapping::getDiabetesDescription);
        put("CARDIOVASCULAR_DISEASE", ValueMapping::getCardiovascularDiseaseDescription);
        put("IMAGE_SRC", ValueMapping::getImageSourceDescription);
        put("DIA_PERIO", ValueMapping::getPerioDiseaseDescription);
        put("DIS_LOC", ValueMapping::getLocationDescription);
        put("DIS_CLASS", ValueMapping::getOsteomyelitisTypeDescription);
        put("DIS_CLASS ", ValueMapping::getOsteomyelitisTypeDescription);
        put("MR_STAGE", ValueMapping::getStageDescription);
        put("MR_HOWTOTAKE", ValueMapping::getMedicationMethodDescription);
        put("MR_HOWLONG", ValueMapping::getMedicationDurationDescription);
        put("EXTRACTION", ValueMapping::getCommonOXDescription);
        put("TRAUMA", ValueMapping::getCommonOXDescription);
        put("IMPLANT", ValueMapping::getCommonOXDescription);
        put("BONE_SUR", ValueMapping::getCommonOXDescription);
        put("FIRST_TREAT", ValueMapping::getFirstTreatDescription);
        put("RECUR", ValueMapping::getCommonOXDescription);
        put("DI_DISEASE", ValueMapping::getCraniofacialDescription);
        put("DI_TIME", ValueMapping::getDataTimeDescription);
        put("DI_DETAIL", ValueMapping::getDiagnosisDetailDescription);
        put("DI_NAME", ValueMapping::getDiagnosisNameDescription);
        put("DI_LOC", ValueMapping::getLesionLocationDescription);
        put("MH_HIGHBLOOD", ValueMapping::getCommonOXDescription);
        put("MH_OSTEOPROSIS", ValueMapping::getCommonOXDescription);
        put("ORIGIN_INF", ValueMapping::getCommonOXDescription);
    }};


    private static final Map<String, String> DiseaseClassMap = new HashMap<>() {{
        put("A", "치주질환");
        put("B", "골수염");
        put("C", "구강암");
        put("D", "두개안면");
        put("E", "대조군 1");
    }};

    // 기관 ID 매핑
    private static final Map<String, String> institutionMap = new HashMap<>() {{
        put("원광대학교", "원광대학교");
        put("고려대학교", "고려대학교");
        put("서울대학교", "서울대학교");
        put("국립암센터", "국립암센터");
        put("단국대학교", "단국대학교");
        put("조선대학교", "조선대학교");
        put("보라매병원", "보라매병원");
    }};

    // Tooth 상태 매핑
    private static final Map<String, String> toothStateMap = new HashMap<>() {{
        put("1", "정상");
        put("2", "보철");
        put("3", "임플란트");
        put("4", "브릿지");
        put("5", "기타");
    }};

    // Tooth 상태를 반환하는 메소드
    public static String getToothStateDescription(String value) {
        return toothStateMap.getOrDefault(value, "Unknown 상태");
    }


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

    // 발생부위 (DIS_LOC) 매핑
    private static final Map<String, String> locationMap = new HashMap<>() {{
        put("1", "maxilla");
        put("2", "mandible");
        put("3", "both");
    }};

    // 골수염 종류 (DIS_CLASS) 매핑
    private static final Map<String, String> osteomyelitisTypeMap = new HashMap<>() {{
        put("1", "osteomyelitis");
        put("2", "MRONJ");
        put("3", "both");
    }};

    // Stage (MR_STAGE) 매핑
    private static final Map<String, String> stageMap = new HashMap<>() {{
        put("0", "0기");
        put("1", "1기");
        put("2", "2기");
        put("3", "3기");
    }};

    // 약물복용방법 (MR_HOWTOTAKE) 매핑
    private static final Map<String, String> medicationMethodMap = new HashMap<>() {{
        put("1", "Oral");
        put("2", "IV");
        put("3", "both");
    }};

    // 약물복용기간 (MR_HOWLONG) 매핑
    private static final Map<String, String> medicationDurationMap = new HashMap<>() {{
        put("1", "1년 미만");
        put("2", "1~2년");
        put("3", "2~3년");
        put("4", "3~4년");
        put("5", "4~5년");
        put("6", "5~6년");
        put("7", "6~7년");
        put("8", "7~8년");
        put("9", "8~9년");
        put("10", "9~10년");
        put("11", "10년 이상");
    }};

    // 공통 매핑 (O와 X로 이루어진 매핑)
    private static final Map<String, String> commonOXMap = new HashMap<>() {{
        put("1", "X");
        put("2", "O");
    }};

    // 처음 처치법 매핑
    private static final Map<String, String> firstTreatMap = new HashMap<>() {{
        put("1", "sequestrectomy");
        put("2", "saucerization");
        put("3", "decortification");
        put("4", "mandibulectomy (재건 포함)");
        put("5", "수술 없이 항생제 치료");
    }};

    // 두개안면 기형 여부 매핑
    private static final Map<String, String> craniofacialMap = new HashMap<>() {{
        put("1", "두개안면기형");
        put("2", "Others");
    }};

    // 데이터 획득 시기 매핑
    private static final Map<String, String> dataTimeMap = new HashMap<>() {{
        put("1", "초진 데이터");
        put("2", "악교정 술전 교정 후 데이터");
    }};

    // 세부진단 매핑
    private static final Map<String, String> diagnosisDetailMap = new HashMap<>() {{
        put("1", "skeletal class (I,II,III)");
        put("2", "Asymmetry");
        put("3", "Skeletal Canting and yawing");
        put("4", "Facial cleft");
        put("5", "Dento Alveolar Deformity");
        put("6", "기타");
    }};

    // 진단명 매핑 (DI_NAME)
    private static final Map<String, String> diagnosisNameMap = new HashMap<>() {{
        put("1", "SCC");
        put("2", "그 외");
        put("3", "정상");
    }};

    // 병소부위 매핑 (DI_LOC)
    private static final Map<String, String> lesionLocationMap = new HashMap<>() {{
        put("1", "Tongue");
        put("2", "FOM");
        put("3", "Buccal 출다");
        put("4", "Upper gum");
        put("5", "Lower gum");
        put("6", "RMT");
        put("7", "Palate");
        put("8", "Sinus");
        put("9", "Tonsil");
    }};


    private static String getAgeRange(String value) {
        if (!isNumeric(value)) {
            return ""; // 숫자가 아닌 경우 제외
        }

        int age = Integer.parseInt(value);
        if (age < 10) {
            return "0-9";
        } else if (age >= 10 && age <= 20) {
            return "10-20";
        } else if (age >= 21 && age <= 30) {
            return "21-30";
        } else if (age >= 31 && age <= 40) {
            return "31-40";
        } else if (age >= 41 && age <= 50) {
            return "41-50";
        } else if (age >= 51 && age <= 60) {
            return "51-60";
        } else if (age >= 61 && age <= 70) {
            return "61-70";
        } else if (age >= 71 && age <= 80) {
            return "71-80";
        } else if (age >= 81 && age <= 90) {
            return "81-90";
        } else {
            return "90+";
        }
    }


    // 체중 필터링 구간 설정
    private static String getWeightRange(String value) {
        if (!isNumeric(value)) {
            return ""; // 숫자가 아닌 경우 제외
        }

        long weight = Long.parseLong(value);
        if (weight < 40) {
            return "40 미만";
        } else if (weight >= 40 && weight <= 50) {
            return "40-50";
        } else if (weight >= 51 && weight <= 60) {
            return "51-60";
        } else if (weight >= 61 && weight <= 70) {
            return "61-70";
        } else if (weight >= 71 && weight <= 80) {
            return "71-80";
        } else if (weight >= 81 && weight <= 90) {
            return "81-90";
        } else {
            return "91+";
        }
    }


    // 키 필터링 구간 설정
    private static String getHeightRange(String value) {
        if (!isNumeric(value)) {
            return ""; // 숫자가 아닌 경우 제외
        }

        int height = Integer.parseInt(value.trim());
        if (height < 140) {
            return "140 미만";
        } else if (height >= 140 && height <= 150) {
            return "140-150";
        } else if (height >= 151 && height <= 160) {
            return "151-160";
        } else if (height >= 161 && height <= 170) {
            return "161-170";
        } else if (height >= 171 && height <= 180) {
            return "171-180";
        } else if (height >= 181 && height <= 190) {
            return "181-190";
        } else {
            return "190+";
        }
    }


    private static String getYearRange(String value) {
        if (!isNumeric(value)) {
            return ""; // 숫자가 아닌 경우 제외
        }

        int year = Integer.parseInt(value.trim());
        if (year >= 1201 && year <= 1212) {
            return "12년";
        }  else if (year >= 1101 && year <= 1112) {
            return "11년";
        }   else if (year >= 1001 && year <= 1012) {
            return "10년";
        }   else if (year >= 901 && year <= 912) {
            return "09년";
        } else if (year >= 801 && year <= 812) {
            return "08년";
        }  else if (year <= 712) {
            return "07년 이하";
        } else if (year >= 1301 && year <= 1312) {
            return "13년";
        } else if (year >= 1401 && year <= 1412) {
            return "14년";
        } else if (year >= 1501 && year <= 1512) {
            return "15년";
        } else if (year >= 1601 && year <= 1612) {
            return "16년";
        } else if (year >= 1701 && year <= 1712) {
            return "17년";
        } else if (year >= 1801 && year <= 1812) {
            return "18년";
        } else if (year >= 1901 && year <= 1912) {
            return "19년";
        } else if (year >= 2001 && year <= 2012) {
            return "20년";
        } else if (year >= 2101 && year <= 2112) {
            return "21년";
        } else if (year >= 2201 && year <= 2212) {
            return "22년";
        } else if (year >= 2301 && year <= 2312) {
            return "23년";
        } else if (year >= 2401 && year <= 2412) {
            return "24년";
        } else {
            return "";
        }
    }





    // 특정 매핑을 가져오는 메소드
    public static String getInstitutionDescription(String value) {
        return institutionMap.getOrDefault(value, "");
    }

    public static String getDiseaseClass(String value) {
        return DiseaseClassMap.getOrDefault(value, "");
    }

    public static String getGenderDescription(String value) {
        return genderMap.getOrDefault(value, "");
    }

    public static String getSmokingDescription(String value) {
        return smokingMap.getOrDefault(value, "");
    }

    public static String getAlcoholDescription(String value) {
        return alcoholMap.getOrDefault(value, "");
    }

    public static String getDiabetesDescription(String value) {
        return diabetesMap.getOrDefault(value, "");
    }

    public static String getCardiovascularDiseaseDescription(String value) {
        return cardiovascularDiseaseMap.getOrDefault(value, "");
    }

    public static String getImageSourceDescription(String value) {
        return imageSourceMap.getOrDefault(value, "");
    }

    public static String getPerioDiseaseDescription(String value) {
        return perioDiseaseMap.getOrDefault(value, "");
    }

    public static String getLocationDescription(String value) {
        return locationMap.getOrDefault(value, "");
    }

    public static String getOsteomyelitisTypeDescription(String value) {
        return osteomyelitisTypeMap.getOrDefault(value, "");
    }

    public static String getStageDescription(String value) {
        return stageMap.getOrDefault(value, "");
    }

    public static String getMedicationMethodDescription(String value) {
        return medicationMethodMap.getOrDefault(value, "");
    }

    public static String getMedicationDurationDescription(String value) {
        return medicationDurationMap.getOrDefault(value, "");
    }


    public static String getFirstTreatDescription(String value) {
        return firstTreatMap.getOrDefault(value, "");
    }
    public static String getCommonOXDescription(String value) {
        return commonOXMap.getOrDefault(value, "");
    }
    public static String getCraniofacialDescription(String value) {
        return craniofacialMap.getOrDefault(value, "");
    }

    public static String getDataTimeDescription(String value) {
        return dataTimeMap.getOrDefault(value, "");
    }
    // 진단명(DI_NAME) 매핑 메소드
    public static String getDiagnosisNameDescription(String value) {
        return diagnosisNameMap.getOrDefault(value, "");
    }

    // 병소부위(DI_LOC) 매핑 메소드
    public static String getLesionLocationDescription(String value) {
        return lesionLocationMap.getOrDefault(value, "");
    }

    public static String getDiagnosisDetailDescription(String value) {
        String[] selections = value.split(",");
        StringBuilder details = new StringBuilder();
        for (String selection : selections) {
            String description = diagnosisDetailMap.getOrDefault(selection.trim(), "");
            if (!details.isEmpty()) {
                details.append(". ");
            }
            details.append(description);
        }
        return details.toString();
    }


    private static boolean isNumeric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false; // null 또는 빈 문자열은 숫자가 아님
        }
        try {
            Long.parseLong(value.trim());
            return true; // 숫자로 변환 가능하면 true
        } catch (NumberFormatException e) {
            return false; // 변환 불가능하면 false
        }
    }

}
