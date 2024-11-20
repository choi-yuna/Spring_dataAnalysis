package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DataGropedService {

    // 질환별로 데이터를 그룹화하는 메소드
    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by disease. Number of entries: {}", resultList.size());
        log.info("{}",resultList.get(0));

        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");

            if (!groupedData.containsKey(diseaseClass)) {
                groupedData.put(diseaseClass, new HashMap<>());
            }

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            if (!diseaseData.containsKey("title")) {
                diseaseData.put("title", diseaseClass);
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                diseaseData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");

            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
            }

            // subData에 각 기관 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(institutionId);
            subRow.add(String.valueOf(goalCount));
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));

            // 43% 대신 실제 구축율 값 추가
            int buildRate = (int) totalData.get(5);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            subData.add(subRow);
        }

        log.info("Finished grouping by disease. Number of grouped diseases: {}", groupedData.size());
        return formatGroupedData(groupedData);
    }

    public List<Map<String, Object>> groupDataByInstitution(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by institution. Number of entries: {}", resultList.size());

        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("기관");
            String diseaseClass = (String) item.get("질환");

            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
            }

            Map<String, Object> institutionData = groupedData.get(institutionId);
            if (!institutionData.containsKey("title")) {
                institutionData.put("title", institutionId);
                institutionData.put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                institutionData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");

            // "목표건수"가 null일 경우 0으로 설정
            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);

            // "라벨링건수"가 null일 경우 0으로 설정
            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            // "1차검수"가 null일 경우 0으로 설정
            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            // "데이터구성검수"가 null일 경우 0으로 설정
            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            // "2차검수"가 null일 경우 0으로 설정
            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
            }

            // subData에 각 질환 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass);
            subRow.add(String.valueOf(goalCount));
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));

            // 실제 구축율 값 추가
            int buildRate = (int) totalData.get(5);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            subData.add(subRow);
        }

        log.info("Finished grouping by institution. Number of grouped institutions: {}", groupedData.size());
        return formatGroupedData(groupedData);
    }

    // 질환 ALL 또는 기관 ALL 데이터를 생성하는 통합 메소드
    public Map<String, Object> createAllData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating 'ALL' data for grouping key: {}", groupingKey);

        Map<String, Object> allData = new HashMap<>();
        allData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(6, 0));
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>();  // 데이터를 그룹화

        // 데이터를 그룹화하고 누적
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 기관 또는 질환을 그룹화
            if (!groupedDataMap.containsKey(groupKey)) {
                groupedDataMap.put(groupKey, new HashMap<>());
                groupedDataMap.get(groupKey).put(groupingKey, groupKey);
                groupedDataMap.get(groupKey).put("목표건수", 0);
                groupedDataMap.get(groupKey).put("라벨링건수", 0);
                groupedDataMap.get(groupKey).put("1차검수", 0);
                groupedDataMap.get(groupKey).put("데이터구성검수", 0);
                groupedDataMap.get(groupKey).put("2차검수", 0);
            }

            // 데이터 누적
            Map<String, Object> groupData = groupedDataMap.get(groupKey);

            // null 체크 및 기본값 설정
            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            groupData.put("목표건수", (int) groupData.get("목표건수") + goalCount);

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            groupData.put("1차검수", (int) groupData.get("1차검수") + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);

            // 총합 데이터 누적
            totalData.set(0, totalData.get(0) + goalCount);
            totalData.set(1, totalData.get(1) + labelingCount);
            totalData.set(2, totalData.get(2) + firstCheck);
            totalData.set(3, totalData.get(3) + dataCheck);
            totalData.set(4, totalData.get(4) + secondCheck);
        }

        // 구축율 계산: (2차검수 / 목표건수) * 100
        int secondCheck = totalData.get(4);
        int goalCount = totalData.get(0);

        if (goalCount > 0) {
            double buildRate = (double) secondCheck / goalCount * 100;
            totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
        }

        allData.put("totalData", totalData);

        // subData에 그룹화된 데이터 추가
        List<List<String>> subData = new ArrayList<>();
        for (Map<String, Object> groupData : groupedDataMap.values()) {
            List<String> subRow = new ArrayList<>();
            subRow.add((String) groupData.get(groupingKey));  // '기관' 또는 '질환' 이름
            subRow.add(groupData.get("목표건수").toString());
            subRow.add(groupData.get("라벨링건수").toString());
            subRow.add(groupData.get("1차검수").toString());
            subRow.add(groupData.get("데이터구성검수").toString());
            subRow.add(groupData.get("2차검수").toString());

            int buildRateForGroup = 0;
            if ((int) groupData.get("목표건수") > 0) {
                buildRateForGroup = (int) groupData.get("2차검수") * 100 / (int) groupData.get("목표건수");
            }
            subRow.add(buildRateForGroup + ""); // 구축율을 추가 (백분율)

            subData.add(subRow);
        }

        allData.put("subData", subData);

        log.info("Finished creating 'ALL' data for grouping key: {}", groupingKey);
        return allData;
    }

    // 그룹화된 데이터를 형식에 맞게 변환하는 메소드
    public List<Map<String, Object>> formatGroupedData(Map<String, Map<String, Object>> groupedData) {
        List<Map<String, Object>> formattedData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedData.entrySet()) {
            Map<String, Object> institutionOrDiseaseData = entry.getValue();
            Map<String, Object> result = new HashMap<>();
            result.put("title", institutionOrDiseaseData.get("title"));
            result.put("totalData", institutionOrDiseaseData.get("totalData"));
            result.put("subData", institutionOrDiseaseData.get("subData"));
            formattedData.add(result);
        }
        return formattedData;
    }
}
