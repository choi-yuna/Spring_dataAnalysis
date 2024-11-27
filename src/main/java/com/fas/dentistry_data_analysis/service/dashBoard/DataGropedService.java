package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DataGropedService {
    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by disease. Number of entries: {}", resultList.size());

        // 질환별 목표건수를 고정
        Map<String, Integer> diseaseGoalCounts = new HashMap<>();
        diseaseGoalCounts.put("치주질환", 6500);
        diseaseGoalCounts.put("골수염", 4500);
        diseaseGoalCounts.put("두개안면", 1000);
        diseaseGoalCounts.put("구강암", 1000);
        diseaseGoalCounts.put("대조군 1", 1000);

        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Set<String> processedImageIds = new HashSet<>();  // IMAGE_ID 중복 체크

        // 질환별로 그룹화하기 전에 기관별로 목표건수를 계산
        Map<String, Set<String>> diseaseInstitutions = new HashMap<>();

        // 기관별로 질환을 그룹화
        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");

            if (!diseaseInstitutions.containsKey(diseaseClass)) {
                diseaseInstitutions.put(diseaseClass, new HashSet<>());
            }
            diseaseInstitutions.get(diseaseClass).add(institutionId);
        }

        // 각 질환에 대해 목표건수를 기관 수에 맞게 계산
        Map<String, Integer> diseaseInstitutionGoalCounts = new HashMap<>();
        for (String diseaseClass : diseaseInstitutions.keySet()) {
            int institutionCount = diseaseInstitutions.get(diseaseClass).size();
            int goalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0);
            // 기관 수만큼 목표건수를 곱한 값을 저장 (totalData에서만 사용)
            diseaseInstitutionGoalCounts.put(diseaseClass, goalCount * institutionCount);
        }

        // 데이터를 질환별로 그룹화
        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");
            String imageId = (String) item.get("IMAGE_ID");

            // 질환별 데이터 그룹화
            if (!groupedData.containsKey(diseaseClass)) {
                groupedData.put(diseaseClass, new HashMap<>());
            }

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            if (!diseaseData.containsKey("title")) {
                diseaseData.put("title", diseaseClass);
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                diseaseData.put("subData", new ArrayList<>());
            }

            // 중복된 IMAGE_ID는 건너뜁니다.
            if (processedImageIds.contains(imageId)) {
                continue;
            }

            // IMAGE_ID를 처리한 것으로 표시
            processedImageIds.add(imageId);

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");

            // 질환별로 목표건수를 기관 수에 맞게 합산 (전체 목표건수)
            int goalCount = diseaseInstitutionGoalCounts.getOrDefault(diseaseClass, 0);

            // 목표건수를 한번만 설정
            if (totalData.get(0) == 0) {
                totalData.set(0, goalCount); // 목표건수는 질환별로 누적된 값
            }

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            int buildRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;
            totalData.set(5, buildRate);

            // subData에 각 기관 정보 추가 (기관별 목표건수는 고유값 유지)
            List<String> subRow = new ArrayList<>();
            int institutionGoalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0); // 기관별 목표건수는 고유한 값 유지
            subRow.add(institutionId == null ? "Unknown Institution" : institutionId);
            subRow.add(String.valueOf(institutionGoalCount)); // 각 기관의 목표건수는 고유값 유지
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));
            subRow.add(String.valueOf(buildRate)); // 구축율 추가

            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(institutionId)) {
                    // 같은 기관의 데이터를 합산
                    existingSubRow.set(1, String.valueOf(institutionGoalCount)); // 목표건수는 고유값 유지
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(4, String.valueOf(Integer.parseInt(existingSubRow.get(4)) + dataCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + secondCheck));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                subData.add(subRow);
            }
        }

        return formatGroupedData(groupedData);
    }

    public List<Map<String, Object>> groupDataByInstitution(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by institution. Number of entries: {}", resultList.size());

        // 질환별 목표건수를 고정
        Map<String, Integer> diseaseGoalCounts = new HashMap<>();
        diseaseGoalCounts.put("치주질환", 6500);
        diseaseGoalCounts.put("골수염", 4500);
        diseaseGoalCounts.put("두개안면", 1000);
        diseaseGoalCounts.put("구강암", 1000);
        diseaseGoalCounts.put("대조군 1", 1000);

        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Set<String> processedImageIds = new HashSet<>();  // IMAGE_ID 중복 체크

        // 기관별로 데이터 그룹화
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String imageId = (String) item.get("IMAGE_ID");

            // 기관별 데이터 그룹화
            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
                groupedData.get(institutionId).put("title", institutionId);
                groupedData.get(institutionId).put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                groupedData.get(institutionId).put("subData", new ArrayList<>());
            }

            Map<String, Object> institutionData = groupedData.get(institutionId);

            // 중복된 IMAGE_ID는 건너뜁니다.
            if (processedImageIds.contains(imageId)) {
                continue;
            }

            // IMAGE_ID를 처리한 것으로 표시
            processedImageIds.add(imageId);

            // 각 질환별 목표건수를 고정
            int goalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0);

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");

            // 목표건수는 첫 번째 질환에 대해만 누적하고, 그 후에는 누적하지 않음
            if (totalData.get(0) == 0) {  // 첫 번째 질환에 대해서만 목표건수를 누적
                totalData.set(0, goalCount);  // 목표건수 합산
            }

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            int buildRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;
            totalData.set(5, buildRate);  // 2차검수에 대한 구축율 추가

            // subData에 각 질환 정보 추가 (기관별로 목표건수를 고유하게 유지)
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass == null ? "Unknown Disease" : diseaseClass);
            subRow.add(String.valueOf(goalCount)); // 목표건수는 고유한 값
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));
            subRow.add(String.valueOf(buildRate)); // 구축율 추가

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(diseaseClass)) {
                    // 같은 질환의 데이터를 합산
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(4, String.valueOf(Integer.parseInt(existingSubRow.get(4)) + dataCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + secondCheck));
                    int existingBuildRate = Integer.parseInt(existingSubRow.get(5));
                    existingSubRow.set(6, String.valueOf(existingBuildRate)); // 업데이트된 구축율
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                subData.add(subRow);
            }
        }

        return formatGroupedData(groupedData);
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


    public Map<String, Object> createAllData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating 'ALL' data for grouping key: {}", groupingKey);

        Map<String, Object> allData = new HashMap<>();
        allData.put("title", title);

        // 질환별 고정 목표 건수 설정
        Map<String, Integer> diseaseGoalCounts = new HashMap<>();
        diseaseGoalCounts.put("치주질환", 6500);
        diseaseGoalCounts.put("골수염", 4500);
        diseaseGoalCounts.put("두개안면", 1000);
        diseaseGoalCounts.put("구강암", 1000);
        diseaseGoalCounts.put("대조군 1", 1000);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(6, 0));
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>();  // 데이터를 그룹화

        // 데이터를 그룹화하고 누적
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 기관 또는 질환을 그룹화
            String diseaseClass = (String) item.get("DISEASE_CLASS"); // 질환별 목표건수를 가져오기 위해 추가

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

            // 고정 목표 건수 설정 (질환별로 목표 건수를 설정)
            int goalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0);
            groupData.put("목표건수", goalCount); // 목표건수는 고정값으로 설정

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            groupData.put("1차검수", (int) groupData.get("1차검수") + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);

            // 총합 데이터 누적 (목표건수는 고정값으로 더함)
            totalData.set(0, totalData.get(0) + goalCount);  // 목표건수 합산
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
        int totalGoalCount = 0; // 목표건수의 합계를 저장할 변수

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

            // 목표건수 합산
            totalGoalCount += (int) groupData.get("목표건수");

            subData.add(subRow);
        }

        // "합계" 행 추가
        List<String> totalRow = new ArrayList<>();
        totalRow.add("합계");
        totalRow.add(String.valueOf(totalGoalCount));  // 목표건수의 합계
        totalRow.add(String.valueOf(totalData.get(1))); // 라벨링건수 합계
        totalRow.add(String.valueOf(totalData.get(2))); // 1차검수 합계
        totalRow.add(String.valueOf(totalData.get(3))); // 데이터구성검수 합계
        totalRow.add(String.valueOf(totalData.get(4))); // 2차검수 합계
        totalRow.add(String.valueOf(totalData.get(5))); // 구축율 합계

        subData.add(totalRow); // "합계" 행을 subData에 추가

        allData.put("subData", subData);

        log.info("Finished creating 'ALL' data for grouping key: {}", groupingKey);
        return allData;
    }



}