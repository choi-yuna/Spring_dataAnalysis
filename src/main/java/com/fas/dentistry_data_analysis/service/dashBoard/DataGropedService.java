package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataGropedService {

    private static final Map<String, Integer> diseaseGoalCounts = new HashMap<>();
    private static final List<String> diseaseOrder = Arrays.asList("치주질환", "골수염", "대조군 1", "구강암", "두개안면");

    static {
        // static 블록에서 초기화
        diseaseGoalCounts.put("치주질환", 6500);
        diseaseGoalCounts.put("골수염", 4500);
        diseaseGoalCounts.put("두개안면", 1000);
        diseaseGoalCounts.put("구강암", 1000);
        diseaseGoalCounts.put("대조군 1", 1000);
    }


    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {

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
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(8, 0))); // 초기값 설정
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
            totalData.set(4, totalData.get(4) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(6, totalData.get(6) + secondCheck);


            // subData에 각 기관 정보 추가 (기관별 목표건수는 고유값 유지)
            List<String> subRow = new ArrayList<>();
            int institutionGoalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0); // 기관별 목표건수는 고유한 값 유지
            subRow.add(institutionId == null ? "Unknown Institution" : institutionId);
            subRow.add(String.valueOf(institutionGoalCount)); // 각 기관의 목표건수는 고유값 유지
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add("0");  // 1차 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)
            subRow.add(String.valueOf(dataCheck));
            subRow.add("0");  // 데이터 구성 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)
            subRow.add(String.valueOf(secondCheck));
            subRow.add("0");  // 2차 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)

            // subData에 기관 정보 추가
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(institutionId)) {
                    // 같은 기관의 데이터를 합산
                    existingSubRow.set(1, String.valueOf(institutionGoalCount)); // 목표건수는 고유값 유지
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + dataCheck));
                    existingSubRow.set(7, String.valueOf(Integer.parseInt(existingSubRow.get(7)) + secondCheck));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                subData.add(subRow);
            }
        }

        // 이제 각 기관별로 구축율 계산
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(3));
                int dataCheck = Integer.parseInt(subRow.get(5));
                int secondCheck = Integer.parseInt(subRow.get(7));

                // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int dataCheckRate = (institutionGoalCount > 0) ? (int) ((dataCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                // 각 검수 항목별 구축율을 subRow에 추가
                subRow.set(4, String.valueOf(firstCheckRate));  // 1차검수 구축율 (subRow[3]에 설정)
                subRow.set(6, String.valueOf(dataCheckRate));  // 데이터구성검수 구축율 (subRow[5]에 설정)
                subRow.set(8, String.valueOf(secondCheckRate)); // 2차검수 구축율 (subRow[7]에 설정)

            }
        }
// 각 질환별 구축율 계산 (totalData에 구축율을 추가)
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int goalCount = totalData.get(0); // 목표건수
            int firstCheck = totalData.get(2); // 1차검수
            int dataCheck = totalData.get(4); // 데이터구성검수
            int secondCheck = totalData.get(6); // 2차검수

            // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
            int firstCheckRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
            int dataCheckRate = (goalCount > 0) ? (int) ((dataCheck / (double) goalCount) * 100) : 0;
            int secondCheckRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;

            // totalData에 구축율 추가
            totalData.set(3, firstCheckRate);  // 1차검수 구축율
            totalData.set(5, dataCheckRate);   // 데이터구성검수 구축율
            totalData.set(7, secondCheckRate); // 2차검수 구축율
        }


        // 질환별로 기관을 가나다 순으로 정렬
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            subData.sort(Comparator.comparing(subRow -> subRow.get(0))); // 기관명을 기준으로 정렬
        }

        // 질환별 순서대로 정렬
        List<Map<String, Object>> sortedDiseaseData = new ArrayList<>();
        for (String disease : diseaseOrder) {
            if (groupedData.containsKey(disease)) {
                sortedDiseaseData.add(groupedData.get(disease));
            }
        }

        return sortedDiseaseData;
    }


    public List<Map<String, Object>> groupDataByInstitution(List<Map<String, Object>> resultList) {

        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Set<String> processedImageIds = new HashSet<>();  // IMAGE_ID 중복 체크

        // 기관별 질환을 추적하는 구조
        Map<String, Set<String>> institutionDiseases = new HashMap<>();  // 기관별 질환 목록


        // 기관별로 데이터 그룹화
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String imageId = (String) item.get("IMAGE_ID");

            // 기관별 데이터 그룹화
            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
                groupedData.get(institutionId).put("title", institutionId);
                groupedData.get(institutionId).put("totalData", new ArrayList<>(Collections.nCopies(8, 0))); // 초기값 설정
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

            // 기관에 속한 질환들의 목표건수를 합산 (기관마다 속한 모든 질환의 목표건수를 더함)
            institutionDiseases.putIfAbsent(institutionId, new HashSet<>());
            Set<String> institutionDiseaseSet = institutionDiseases.get(institutionId);

            // 해당 질환이 아직 추가되지 않았다면 목표건수 합산
            if (!institutionDiseaseSet.contains(diseaseClass)) {
                institutionDiseaseSet.add(diseaseClass);
                totalData.set(0, totalData.get(0) + goalCount);  // 목표건수 합산
            }

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(4, totalData.get(4) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(6, totalData.get(6) + secondCheck);


            // subData에 각 질환 정보 추가 (기관별로 목표건수를 고유하게 유지)
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass == null ? "Unknown Disease" : diseaseClass);
            subRow.add(String.valueOf(goalCount)); // 목표건수는 고유한 값
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add("0");  // 1차 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)
            subRow.add(String.valueOf(dataCheck));
            subRow.add("0");  // 데이터 구성 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)
            subRow.add(String.valueOf(secondCheck));
            subRow.add("0");  // 데이터 구성 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(diseaseClass)) {
                    // 같은 질환의 데이터를 합산
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + dataCheck));
                    existingSubRow.set(7, String.valueOf(Integer.parseInt(existingSubRow.get(7)) + secondCheck));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                subData.add(subRow);
            }
        }

        // 각 기관별로 subData (질환별) diseaseOrder 순서대로 정렬
        for (Map<String, Object> institutionData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(3));
                int dataCheck = Integer.parseInt(subRow.get(5));
                int secondCheck = Integer.parseInt(subRow.get(7));

                // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int dataCheckRate = (institutionGoalCount > 0) ? (int) ((dataCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                // 각 검수 항목별 구축율을 subRow에 추가
                subRow.set(4, String.valueOf(firstCheckRate));  // 1차검수 구축율 (subRow[3]에 설정)
                subRow.set(6, String.valueOf(dataCheckRate));  // 데이터구성검수 구축율 (subRow[5]에 설정)
                subRow.set(8, String.valueOf(secondCheckRate)); // 2차검수 구축율 (subRow[7]에 설정)

            }
            // diseaseOrder 순서에 맞게 정렬
            subData.sort((subRowA, subRowB) -> {
                String diseaseA = subRowA.get(0);
                String diseaseB = subRowB.get(0);
                return Integer.compare(diseaseOrder.indexOf(diseaseA), diseaseOrder.indexOf(diseaseB));
            });
        }
        // 각 질환별 구축율 계산 (totalData에 구축율을 추가)
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int goalCount = totalData.get(0); // 목표건수
            int firstCheck = totalData.get(2); // 1차검수
            int dataCheck = totalData.get(4); // 데이터구성검수
            int secondCheck = totalData.get(6); // 2차검수

            // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
            int firstCheckRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
            int dataCheckRate = (goalCount > 0) ? (int) ((dataCheck / (double) goalCount) * 100) : 0;
            int secondCheckRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;

            // totalData에 구축율 추가
            totalData.set(3, firstCheckRate);  // 1차검수 구축율
            totalData.set(5, dataCheckRate);   // 데이터구성검수 구축율
            totalData.set(7, secondCheckRate); // 2차검수 구축율
        }


        // 기관을 가나다 순으로 정렬
        List<Map<String, Object>> sortedInstitutions = groupedData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        return sortedInstitutions;
    }

    public Map<String, Object> createInstitutionData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating institution data for grouping key: {}", groupingKey);

        Map<String, Object> institutionData = new HashMap<>();
        institutionData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(8, 0)); // 목표건수, 라벨링건수, 1차검수, 데이터구성검수, 2차검수, 구축율
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 기관별로 그룹화된 데이터
        Map<String, Map<String, Integer>> diseaseStatsMap = new HashMap<>(); // 질환별 통계 누적
        Map<String, Set<String>> processedDiseaseInstitutions = new HashMap<>(); // 질환별 처리된 기관 기록

        // 기관별로 데이터를 그룹화하고 누적
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 기관명
            String diseaseClass = (String) item.get("DISEASE_CLASS"); // 질환 구분


            // 기관별로 그룹화된 데이터 초기화
            if (!groupedDataMap.containsKey(groupKey)) {
                groupedDataMap.put(groupKey, new HashMap<>());
                groupedDataMap.get(groupKey).put(groupingKey, groupKey);
                groupedDataMap.get(groupKey).put("목표건수", 0); // 목표건수는 0으로 초기화
                groupedDataMap.get(groupKey).put("라벨링건수", 0);
                groupedDataMap.get(groupKey).put("1차검수", 0);
                groupedDataMap.get(groupKey).put("데이터구성검수", 0);
                groupedDataMap.get(groupKey).put("2차검수", 0);
            }

            // 질환별 통계 초기화
            if (!diseaseStatsMap.containsKey(diseaseClass)) {
                diseaseStatsMap.put(diseaseClass, new HashMap<>());
                diseaseStatsMap.get(diseaseClass).put("목표건수", 0);
                diseaseStatsMap.get(diseaseClass).put("라벨링건수", 0);
                diseaseStatsMap.get(diseaseClass).put("1차검수", 0);
                diseaseStatsMap.get(diseaseClass).put("데이터구성검수", 0);
                diseaseStatsMap.get(diseaseClass).put("2차검수", 0);
            }

            // 질환별 목표건수 계산 (정해진 목표건수 사용)
            int diseaseGoalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0); // diseaseGoalCounts에서 값 가져오기
            log.info("Disease: {}, Goal count: {}", diseaseClass, diseaseGoalCount);

            // 각 항목에 대해 누적 (기관별로)
            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            Map<String, Object> groupData = groupedDataMap.get(groupKey);
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            groupData.put("1차검수", (int) groupData.get("1차검수") + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);

            // 질환별 통계 누적
            Map<String, Integer> diseaseStats = diseaseStatsMap.get(diseaseClass);
            diseaseStats.put("목표건수", diseaseStats.get("목표건수") + diseaseGoalCount);  // 질환별 목표건수 누적
            diseaseStats.put("라벨링건수", diseaseStats.get("라벨링건수") + labelingCount);
            diseaseStats.put("1차검수", diseaseStats.get("1차검수") + firstCheck);
            diseaseStats.put("데이터구성검수", diseaseStats.get("데이터구성검수") + dataCheck);
            diseaseStats.put("2차검수", diseaseStats.get("2차검수") + secondCheck);

            // 질환별로 처리된 기관 기록 초기화
            if (!processedDiseaseInstitutions.containsKey(diseaseClass)) {
                processedDiseaseInstitutions.put(diseaseClass, new HashSet<>());
            }

            // 질환별로 목표건수 누적 (해당 질환을 가진 기관에 대해서만 한 번만 목표건수 추가)
            if (!processedDiseaseInstitutions.get(diseaseClass).contains(groupKey)) {
                processedDiseaseInstitutions.get(diseaseClass).add(groupKey); // 처리된 기관 목록에 추가

                // 기존 목표건수 가져오기
                int currentGoalCount = (int) groupedDataMap.get(groupKey).get("목표건수");

                // 새 목표건수를 누적
                groupedDataMap.get(groupKey).put("목표건수", currentGoalCount + diseaseGoalCount);

                // 업데이트된 목표건수 로그
                log.info("Updated goal count for institution: {}, Disease: {}, New Goal Count: {}", groupKey, diseaseClass, currentGoalCount + diseaseGoalCount);
            }

            // 기관별 항목을 totalData에 누적
            totalData.set(1, totalData.get(1) + labelingCount);
            totalData.set(2, totalData.get(2) + firstCheck);
            totalData.set(4, totalData.get(4) + dataCheck);
            totalData.set(6, totalData.get(6) + secondCheck);
        }

        // 질환별 목표건수 계산 및 각 기관에 할당
        for (Map.Entry<String, Set<String>> entry : processedDiseaseInstitutions.entrySet()) {
            String diseaseClass = entry.getKey();
            Set<String> institutions = entry.getValue(); // 해당 질환에 속한 기관들의 Set

            // 질환별 목표건수 계산
            int diseaseGoalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0); // 각 질환별 목표건수
            int totalGoalForDisease = diseaseGoalCount * institutions.size(); // 목표건수 * 기관 수 (중복 제거된 기관 수)


            // 각 기관에 목표건수를 동일하게 할당
            for (String institution : institutions) {
                Map<String, Object> groupData = groupedDataMap.get(institution);
                int currentGoalCount = (int) groupData.get("목표건수");
                groupData.put("목표건수", currentGoalCount + diseaseGoalCount); // 각 기관에 목표건수 할당
            }
        }

        // subData에 그룹화된 데이터 추가
        List<List<String>> subData = new ArrayList<>();
        int totalGoalCount = 0;

        // 기관별로 서브데이터 생성
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            Map<String, Object> groupData = entry.getValue();
            List<String> subRow = new ArrayList<>(Collections.nCopies(9, ""));
            subRow.set(0,(String) groupData.get(groupingKey)); // 기관명 추가

            // 기관별 목표건수 합산
            int totalGroupGoalCount = (int) groupData.get("목표건수"); // 기관별 목표건수

            log.info("Institution: '{}', Total Group Goal Count: {}", groupData.get(groupingKey), totalGroupGoalCount);

            if (totalGroupGoalCount <= 0) {
                log.warn("Institution '{}' has an invalid goal count. Check initialization or disease processing logic.", groupData.get(groupingKey));
            }



            subRow.set(1,String.valueOf(totalGroupGoalCount)); // 기관별 목표건수 합

            // 각 항목에 대한 값 추가
            subRow.set(2,groupData.get("라벨링건수").toString());
            subRow.set(3,groupData.get("1차검수").toString());
            subRow.set(5,groupData.get("데이터구성검수").toString());
            subRow.set(7,groupData.get("2차검수").toString());

            // 기관별 1차 구축율 계산
            int firstCheck = (int) groupData.get("1차검수");
            int firstBuildRate = 0;
            if (totalGroupGoalCount > 0) {
                firstBuildRate = firstCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(4,String.valueOf(firstBuildRate)); // 1차 구축율 추가

            // 기관별 데이터구성검수 구축율 계산
            int dataCheck = (int) groupData.get("데이터구성검수");
            int dataCheckBuildRate = 0;
            if (totalGroupGoalCount > 0) {
                dataCheckBuildRate = dataCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(6,String.valueOf(dataCheckBuildRate)); // 데이터구성검수 구축율 추가

            // 기관별 구축율 계산
            int secondCheck = (int) groupData.get("2차검수");
            int buildRateForGroup = 0;
            if (totalGroupGoalCount > 0) {
                buildRateForGroup = secondCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(8,String.valueOf(buildRateForGroup)); // 2차검수 구축율 추가

            totalGoalCount += totalGroupGoalCount;
            subData.add(subRow);
        }

        // totalData에서 목표건수와 구축율 계산
        totalData.set(0, totalGoalCount);
        int totalFirstCheck = totalData.get(2);
        int totalDataCheck = totalData.get(4);
        int totalSecondCheck = totalData.get(6);
        if (totalGoalCount > 0) {
            // 전체 1차 구축율 계산
            int firstBuildRate = (totalFirstCheck * 100) / totalGoalCount;
            totalData.set(3, firstBuildRate);

            // 전체 데이터구성검수 구축율 계산
            int dataCheckBuildRate = (totalDataCheck * 100) / totalGoalCount;
            totalData.set(5, dataCheckBuildRate);

            // 전체 구축율 계산
            double buildRate = (totalSecondCheck * 100.0) / totalGoalCount;
            totalData.set(7, (int) buildRate);
        }
        institutionData.put("totalData", totalData);
        institutionData.put("subData", subData);

        log.info("Finished creating institution data for grouping key: {}", groupingKey);
        return institutionData;
    }

    public Map<String, Object> createDiseaseData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating disease data for grouping key: {}", groupingKey);

        Map<String, Object> diseaseData = new HashMap<>();
        diseaseData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(8, 0)); // 목표건수, 라벨링건수, 1차검수, 데이터구성검수, 2차검수, 구축율, 1차구축율, 데이터구성검수 구축율
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 기관별로 그룹화된 데이터

        // 기관별로 데이터를 그룹화하고 질환별로 누적
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 기관을 그룹화
            String diseaseClass = (String) item.get("DISEASE_CLASS"); // 질환 구분

            // 기관별로 그룹화된 데이터 초기화
            if (!groupedDataMap.containsKey(groupKey)) {
                groupedDataMap.put(groupKey, new HashMap<>());
                groupedDataMap.get(groupKey).put(groupingKey, groupKey);
                groupedDataMap.get(groupKey).put("목표건수", 0);
                groupedDataMap.get(groupKey).put("라벨링건수", 0);
                groupedDataMap.get(groupKey).put("1차검수", 0);
                groupedDataMap.get(groupKey).put("데이터구성검수", 0);
                groupedDataMap.get(groupKey).put("2차검수", 0);
            }

            // 기관별로 그룹화된 데이터 가져오기
            Map<String, Object> groupData = groupedDataMap.get(groupKey);

            // 질환별 목표건수 누적
            int goalCount = diseaseGoalCounts.getOrDefault(diseaseClass, 0);
            groupData.putIfAbsent(diseaseClass + "_목표건수", goalCount);

            // 각 항목에 대해 누적
            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            groupData.put("1차검수", (int) groupData.get("1차검수") + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);

            // 질환별 항목을 totalData에 누적
            totalData.set(1, totalData.get(1) + labelingCount);
            totalData.set(2, totalData.get(2) + firstCheck);
            totalData.set(4, totalData.get(4) + dataCheck);
            totalData.set(6, totalData.get(6) + secondCheck);
        }

        // subData에 그룹화된 데이터 추가
        List<List<String>> subData = new ArrayList<>();
        int totalGoalCount = 0;

        // 기관별로 서브데이터 생성
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            Map<String, Object> groupData = entry.getValue();
            List<String> subRow = new ArrayList<>(Collections.nCopies(9, ""));
            subRow.set(0, (String) groupData.get(groupingKey)); // 기관명 추가

            // 각 질환별 목표건수의 합산
            int totalGroupGoalCount = 0;
            for (String key : groupData.keySet()) {
                if (key.endsWith("_목표건수")) {
                    totalGroupGoalCount += (int) groupData.get(key);
                }
            }
            subRow.set(1, String.valueOf(totalGroupGoalCount)); // 기관별 목표건수 합

            // 각 항목에 대한 값 추가
            subRow.set(2, groupData.get("라벨링건수").toString());
            subRow.set(3, groupData.get("1차검수").toString());
            subRow.set(5, groupData.get("데이터구성검수").toString());
            subRow.set(7, groupData.get("2차검수").toString());

            // 기관별 1차 구축율 계산
            int firstCheck = (int) groupData.get("1차검수");
            int firstBuildRate = 0;
            if (totalGroupGoalCount > 0) {
                firstBuildRate = firstCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(4, String.valueOf(firstBuildRate)); // 1차 구축율 추가

            // 기관별 데이터구성검수 구축율 계산
            int dataCheck = (int) groupData.get("데이터구성검수");
            int dataCheckBuildRate = 0;
            if (totalGroupGoalCount > 0) {
                dataCheckBuildRate = dataCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(6, String.valueOf(dataCheckBuildRate)); // 데이터구성검수 구축율 추가

            // 기관별 구축율 계산
            int secondCheck = (int) groupData.get("2차검수");
            int buildRateForGroup = 0;
            if (totalGroupGoalCount > 0) {
                buildRateForGroup = secondCheck * 100 / totalGroupGoalCount;
            }
            subRow.set(8, String.valueOf(buildRateForGroup)); // 2차검수 구축율 추가

            totalGoalCount += totalGroupGoalCount;
            subData.add(subRow);
        }

        // subData를 가나다 순으로 정렬 (기관명 기준)
        subData.sort(Comparator.comparing(subRow -> subRow.get(0)));

        // totalData에서 목표건수와 구축율 계산
        totalData.set(0, totalGoalCount);
        int totalFirstCheck = totalData.get(2);
        int totalDataCheck = totalData.get(4);
        int totalSecondCheck = totalData.get(6);
        if (totalGoalCount > 0) {
            // 전체 1차 구축율 계산
            int firstBuildRate = (totalFirstCheck * 100) / totalGoalCount;
            totalData.set(3, firstBuildRate);

            // 전체 데이터구성검수 구축율 계산
            int dataCheckBuildRate = (totalDataCheck * 100) / totalGoalCount;
            totalData.set(5, dataCheckBuildRate);

            // 전체 구축율 계산
            double buildRate = (totalSecondCheck * 100.0) / totalGoalCount;
            totalData.set(7, (int) buildRate);
        }

        diseaseData.put("totalData", totalData);
        diseaseData.put("subData", subData);

        log.info("Finished creating disease data for grouping key: {}", groupingKey);
        return diseaseData;
    }


}