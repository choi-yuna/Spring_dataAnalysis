package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DataGropedService {

    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by disease. Number of entries: {}", resultList.size());

        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Set<String> processedImageIds = new HashSet<>();  // IMAGE_ID 중복 체크

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
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(8, 0))); // 9개 항목으로 수정
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

            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);  // 목표건수

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);  // 라벨링건수

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);  // 1차검수

            // 1차검수 구축율 계산
            int firstCheckBuildRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
            totalData.set(3, firstCheckBuildRate);  // 1차검수 구축율

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(4, totalData.get(4) + dataCheck);  // 데이터구성검수

            // 최종 구축율 계산: 2차검수 / 목표건수 * 100
            int dataBuildRate = (goalCount > 0) ? (int) ((dataCheck / (double) goalCount) * 100) : 0;
            totalData.set(5, dataBuildRate);  // 최종 구축율


            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(6, totalData.get(5) + secondCheck);  // 2차검수

            // 최종 구축율 계산: 2차검수 / 목표건수 * 100
            int finalBuildRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;
            totalData.set(7, finalBuildRate);  // 최종 구축율


            // subData에 각 기관 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(institutionId == null ? "Unknown Institution" : institutionId);
            subRow.add(String.valueOf(goalCount));
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(firstCheckBuildRate)); // 1차검수 구축율 추가
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(dataBuildRate));
            subRow.add(String.valueOf(secondCheck));
            subRow.add(String.valueOf(finalBuildRate));

            // subData에 각 기관 정보 추가
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(institutionId)) {
                    // 같은 기관의 데이터를 합산
                    existingSubRow.set(1, String.valueOf(Integer.parseInt(existingSubRow.get(1)) + goalCount));
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(4, String.valueOf(Integer.parseInt(existingSubRow.get(4)) + firstCheckBuildRate));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + dataCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(6)) + dataBuildRate));
                    existingSubRow.set(6, String.valueOf(Integer.parseInt(existingSubRow.get(7)) + secondCheck));
                    existingSubRow.set(7, String.valueOf(Integer.parseInt(existingSubRow.get(8)) + finalBuildRate));  // 최종 구축율
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
        if (resultList == null || resultList.isEmpty()) {
            log.warn("Input resultList is null or empty.");
            return new ArrayList<>(); // 빈 리스트 반환
        }

        log.info("Starting to group data by institution. Number of entries: {}", resultList.size());

        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Set<String> processedImageIds = new HashSet<>(); // IMAGE_ID 중복 체크

        for (Map<String, Object> item : resultList) {
            if (item == null) {
                log.warn("Null item found in resultList. Skipping.");
                continue; // null 데이터를 건너뜁니다.
            }

            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String imageId = (String) item.get("IMAGE_ID");

            if (institutionId == null) {
                log.warn("Null INSTITUTION_ID found in item: {}", item);
                continue; // null INSTITUTION_ID는 건너뜁니다.
            }
            if (imageId == null) {
                log.warn("Null IMAGE_ID found in item: {}", item);
                continue; // null IMAGE_ID는 건너뜁니다.
            }

            // 기관별 데이터 그룹화
            groupedData.putIfAbsent(institutionId, new HashMap<>());

            Map<String, Object> institutionData = groupedData.get(institutionId);
            if (!institutionData.containsKey("title")) {
                institutionData.put("title", institutionId);
                institutionData.put("totalData", new ArrayList<>(Collections.nCopies(8, 0))); // 9개 항목으로 수정
                institutionData.put("subData", new ArrayList<>());
            }

            // 중복된 IMAGE_ID는 건너뜁니다.
            if (processedImageIds.contains(imageId)) {
                continue;
            }

            // IMAGE_ID를 처리한 것으로 표시
            processedImageIds.add(imageId);

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");

            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);  // 목표건수

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);  // 라벨링건수

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);  // 1차검수

            // 1차검수 구축율 계산
            int firstCheckBuildRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
            totalData.set(3, firstCheckBuildRate);  // 1차검수 구축율

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(4, totalData.get(4) + dataCheck);  // 데이터구성검수

            // 데이터구성검수 구축율 계산
            int dataCheckBuildRate = (goalCount > 0) ? (int) ((dataCheck / (double) goalCount) * 100) : 0;
            totalData.set(5, dataCheckBuildRate);  // 데이터구성검수 구축율

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(6, totalData.get(6) + secondCheck);  // 2차검수

            // 최종 구축율 계산: 2차검수 / 목표건수 * 100
            int finalBuildRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;
            totalData.set(7, finalBuildRate);  // 최종 구축율

            // subData에 각 질환 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass == null ? "Unknown Disease" : diseaseClass);
            subRow.add(String.valueOf(goalCount));  // 목표건수
            subRow.add(String.valueOf(labelingCount));  // 라벨링건수
            subRow.add(String.valueOf(firstCheck));  // 1차검수
            subRow.add(String.valueOf(firstCheckBuildRate));  // 1차검수 구축율
            subRow.add(String.valueOf(dataCheck));  // 데이터구성검수
            subRow.add(String.valueOf(dataCheckBuildRate));  // 데이터구성검수 구축율
            subRow.add(String.valueOf(secondCheck));  // 2차검수
            subRow.add(String.valueOf(finalBuildRate));  // 2차검수

            // subData에 각 기관 정보 추가
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(diseaseClass)) {
                    // 같은 질환의 데이터를 합산
                    existingSubRow.set(1, String.valueOf(Integer.parseInt(existingSubRow.get(1)) + goalCount));
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(4, String.valueOf(Integer.parseInt(existingSubRow.get(4)) + firstCheckBuildRate));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + dataCheck));
                    existingSubRow.set(7, String.valueOf(Integer.parseInt(existingSubRow.get(6)) + dataCheckBuildRate));
                    existingSubRow.set(6, String.valueOf(Integer.parseInt(existingSubRow.get(7)) + secondCheck));
                    existingSubRow.set(8, String.valueOf(Integer.parseInt(existingSubRow.get(8)) + finalBuildRate));  // 최종 구축율
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


    // 질환 ALL 또는 기관 ALL 데이터를 생성하는 메소드
    public Map<String, Object> createAllData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating 'ALL' data for grouping key: {}", groupingKey);

        Map<String, Object> allData = new HashMap<>();
        allData.put("title", title);

        // 항목 수에 맞게 초기화 (9개의 항목으로 수정)
        List<Integer> totalData = new ArrayList<>(Collections.nCopies(8, 0));  // 9개 항목
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>();  // 데이터를 그룹화

        // 데이터를 그룹화하고 누적
        int goalCount;
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
            goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
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
            totalData.set(0, totalData.get(0) + goalCount);  // 목표건수
            totalData.set(1, totalData.get(1) + labelingCount);  // 라벨링건수
            totalData.set(2, totalData.get(2) + firstCheck);  // 1차검수
            totalData.set(4, totalData.get(4) + dataCheck);  // 데이터구성검수
            totalData.set(6, totalData.get(6) + secondCheck);  // 2차검수
        }

        // 구축율 계산: (1차검수 / 목표건수) * 100
        int firstCheck = totalData.get(2);
        goalCount = totalData.get(0);
        int firstCheckBuildRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
        totalData.set(3, firstCheckBuildRate);  // 1차검수 구축율

        // 데이터구성검수 구축율 계산: (데이터구성검수 / 목표건수) * 100
        int dataCheck = totalData.get(4);
        int dataBuildRate = (goalCount > 0) ? (int) ((dataCheck / (double) goalCount) * 100) : 0;
        log.info("{}", dataCheck);
        totalData.set(5, dataBuildRate);  // 데이터구성검수 구축율

        // 2차검수 구축율 계산: (2차검수 / 목표건수) * 100
        int secondCheck = totalData.get(6);
        int finalBuildRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;
        totalData.set(7, finalBuildRate);  // 2차검수 구축율

        allData.put("totalData", totalData);

        // subData에 그룹화된 데이터 추가
        List<List<String>> subData = new ArrayList<>();
        for (Map<String, Object> groupData : groupedDataMap.values()) {
            List<String> subRow = new ArrayList<>();
            subRow.add((String) groupData.get(groupingKey));  // '기관' 또는 '질환' 이름
            subRow.add(groupData.get("목표건수").toString());  // 목표건수
            subRow.add(groupData.get("라벨링건수").toString());  // 라벨링건수
            subRow.add(groupData.get("1차검수").toString());  // 1차검수
            subRow.add(groupData.get("1차검수") != null ? (Integer.parseInt(groupData.get("1차검수").toString()) * 100) / (Integer.parseInt(groupData.get("목표건수").toString())) + "" : "0"); // 1차검수 구축율 추가
            subRow.add(groupData.get("데이터구성검수").toString());  // 데이터구성검수
            subRow.add(groupData.get("데이터구성검수") != null ? (Integer.parseInt(groupData.get("데이터구성검수").toString()) * 100) / (Integer.parseInt(groupData.get("목표건수").toString())) + "" : "0"); // 데이터구성검수 구축율 추가
            subRow.add(groupData.get("2차검수").toString());  // 2차검수
            subRow.add(groupData.get("2차검수") != null ? (Integer.parseInt(groupData.get("2차검수").toString()) * 100) / (Integer.parseInt(groupData.get("목표건수").toString())) + "" : "0"); // 2차검수 구축율 추가

            subData.add(subRow);
        }

        allData.put("subData", subData);

        log.info("Finished creating 'ALL' data for grouping key: {}", groupingKey);
        return allData;
    }



}
