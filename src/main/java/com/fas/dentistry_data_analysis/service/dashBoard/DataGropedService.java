package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataGropedService {

    private static final Map<String, Map<String, Integer>> institutionDiseaseGoals = new HashMap<>();
    private static final List<String> diseaseOrder = Arrays.asList("치주질환", "골수염", "대조군 1", "구강암", "두개안면");

    static {
        // Institution-wise disease goal data
        Map<String, Integer> WKU = new HashMap<>();
        WKU.put("치주질환", 1403);
        WKU.put("골수염", 592);
        institutionDiseaseGoals.put("원광대학교", WKU);

        Map<String, Integer> KRU = new HashMap<>();
        KRU.put("치주질환", 1400);
        KRU.put("두개안면", 400);
        KRU.put("골수염", 1000);
        KRU.put("구강암", 50);
        institutionDiseaseGoals.put("고려대학교", KRU);

        Map<String, Integer> SNU = new HashMap<>();
        SNU.put("치주질환", 2500);
        SNU.put("구강암", 550);
        institutionDiseaseGoals.put("서울대학교", SNU);

        Map<String, Integer> nationalCancerCenter = new HashMap<>();
        nationalCancerCenter.put("구강암", 450);
        institutionDiseaseGoals.put("국립암센터 ", nationalCancerCenter);

        Map<String, Integer> DKU = new HashMap<>();
        DKU.put("골수염", 1408);
        DKU.put("두개안면", 200);
        institutionDiseaseGoals.put("단국대학교", DKU);

        Map<String, Integer> CSU = new HashMap<>();
        CSU.put("골수염", 1260);
        CSU.put("두개안면", 400);
        institutionDiseaseGoals.put("조선대학교", CSU);

        Map<String, Integer> BRM = new HashMap<>();
        BRM.put("치주질환", 1200);
        BRM.put("골수염", 272);
        BRM.put("구강암", 12);
        institutionDiseaseGoals.put("보라매병원", BRM);
    }


    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        Map<String, Map<String, Object>> groupedData = new HashMap<>();
        Map<String, Set<String>> processedInstitutions = new HashMap<>(); // 각 질환별로 처리된 기관 기록

        // 데이터를 질환별로 그룹화
        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");
            // 질환별 데이터 그룹화
            groupedData.putIfAbsent(diseaseClass, new HashMap<>());
            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            diseaseData.putIfAbsent("title", diseaseClass);
            diseaseData.putIfAbsent("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
            diseaseData.putIfAbsent("subData", new ArrayList<>());


            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");

            // 기관별-질환별 목표 건수를 가져오기
            int institutionGoalCount = institutionDiseaseGoals
                    .getOrDefault(institutionId, new HashMap<>())
                    .getOrDefault(diseaseClass, 0);

            // 중복된 기관-질환 목표건수 방지
            processedInstitutions.putIfAbsent(diseaseClass, new HashSet<>());
            Set<String> institutionsForDisease = processedInstitutions.get(diseaseClass);

            if (!institutionsForDisease.contains(institutionId)) {
                totalData.set(0, totalData.get(0) + institutionGoalCount); // 목표건수는 중복 없이 추가
                institutionsForDisease.add(institutionId);
            }

            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // subData에 각 기관 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(institutionId == null ? "Unknown Institution" : institutionId);
            subRow.add(String.valueOf(institutionGoalCount)); // 기관별 목표건수
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add("0"); // 1차검수 구축율
            subRow.add(String.valueOf(secondCheck));
            subRow.add("0"); // 2차검수 구축율

            // subData에 기관 정보 추가 (누적값 처리)
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(institutionId)) {
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount)); // 라벨링 건수 누적
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck)); // 1차검수 건수 누적
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + secondCheck)); // 2차검수 건수 누적
                    exists = true;
                    break;
                }
            }

            // 만약 해당 기관이 subData에 없으면 새로 추가
            if (!exists) {
                subData.add(subRow);
            }
        }

        // 각 기관별로 구축율 계산
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(3));
                int secondCheck = Integer.parseInt(subRow.get(5));

                // 구축율 계산
                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                subRow.set(4, String.valueOf(firstCheckRate));
                subRow.set(6, String.valueOf(secondCheckRate));
            }
        }

        // 질환별로 기관을 정렬
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

        // 기관별로 처리한 질환을 추적하기 위한 구조
        Map<String, Set<String>> processedDiseasesByInstitution = new HashMap<>();

        // 기관별로 데이터 그룹화
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");

            // 기관별 데이터 그룹화
            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
                groupedData.get(institutionId).put("title", institutionId);
                groupedData.get(institutionId).put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                groupedData.get(institutionId).put("subData", new ArrayList<>());

                // 각 기관별로 처리한 질환을 추적하기 위한 Set 초기화
                processedDiseasesByInstitution.put(institutionId, new HashSet<>());
            }

            Map<String, Object> institutionData = groupedData.get(institutionId);

            // 각 질환별 목표 건수를 기관별 데이터에서 가져오기
            int goalCount = institutionDiseaseGoals
                    .getOrDefault(institutionId, new HashMap<>())
                    .getOrDefault(diseaseClass, 0);

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");

            // 목표 건수 합산 (중복 방지)
            if (!processedDiseasesByInstitution.get(institutionId).contains(diseaseClass)) {
                totalData.set(0, totalData.get(0) + goalCount);
                processedDiseasesByInstitution.get(institutionId).add(diseaseClass);
            }

            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // subData에 각 질환 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass == null ? "Unknown Disease" : diseaseClass);
            subRow.add(String.valueOf(goalCount)); // 기관별-질환별 목표 건수
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add("0");  // 데이터 구성 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)
            subRow.add(String.valueOf(secondCheck));
            subRow.add("0");  // 2차 검수 구축율 (구축율은 나중에 계산되므로 초기값은 0)

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            boolean exists = false;
            for (List<String> existingSubRow : subData) {
                if (existingSubRow.get(0).equals(diseaseClass)) {
                    // 같은 질환의 데이터를 합산
                    existingSubRow.set(2, String.valueOf(Integer.parseInt(existingSubRow.get(2)) + labelingCount));
                    existingSubRow.set(3, String.valueOf(Integer.parseInt(existingSubRow.get(3)) + firstCheck));
                    existingSubRow.set(5, String.valueOf(Integer.parseInt(existingSubRow.get(5)) + secondCheck));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                subData.add(subRow);
            }
        }

        // 각 기관별로 subData (질환별) 정렬 및 구축율 계산
        for (Map<String, Object> institutionData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(3));
                int secondCheck = Integer.parseInt(subRow.get(5));

                // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                // 각 검수 항목별 구축율을 subRow에 추가
                subRow.set(4, String.valueOf(firstCheckRate));  // 1차검수 구축율
                subRow.set(6, String.valueOf(secondCheckRate));  // 2차검수 구축율
            }
        }

        // 각 기관의 totalData에서 구축율 계산
        for (Map<String, Object> institutionData : groupedData.values()) {
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            int goalCount = totalData.get(0); // 목표건수
            int firstCheck = totalData.get(2); // 1차검수
            int secondCheck = totalData.get(4); // 2차검수

            // 구축율 계산: (각 항목의 검수 건수 / 목표건수) * 100
            int firstCheckRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
            int secondCheckRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;

            // totalData에 구축율 추가
            totalData.set(3, firstCheckRate);  // 1차검수 구축율
            totalData.set(5, secondCheckRate);   // 2차검수 구축율
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

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(6, 0)); // 초기화된 totalData
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 그룹화된 질환 데이터
        Map<String, Set<String>> processedDiseasesByInstitution = new HashMap<>(); // 기관별 처리된 질환 기록

        // 기관별로 데이터 그룹화 및 처리
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 질환명
            String institutionId = (String) item.get("INSTITUTION_ID"); // 기관 ID

            // 그룹 데이터 초기화
            groupedDataMap.putIfAbsent(groupKey, new HashMap<>());
            Map<String, Object> groupData = groupedDataMap.get(groupKey);
            groupData.putIfAbsent(groupingKey, groupKey);
            groupData.putIfAbsent("목표건수", 0);
            groupData.putIfAbsent("라벨링등록건수", 0);
            groupData.putIfAbsent("라벨링pass건수", 0);
            groupData.putIfAbsent("2차검수", 0);

            // 기관별 처리된 질환 목록 관리
            processedDiseasesByInstitution.putIfAbsent(institutionId, new HashSet<>());
            Set<String> processedDiseases = processedDiseasesByInstitution.get(institutionId);

            // 중복 방지: 이미 처리된 질환은 목표건수 추가하지 않음
            if (!processedDiseases.contains(groupKey)) {
                int diseaseGoalCount = institutionDiseaseGoals
                        .getOrDefault(institutionId, new HashMap<>())
                        .getOrDefault(groupKey, 0);

                // 목표건수 누적
                groupData.put("목표건수", (int) groupData.get("목표건수") + diseaseGoalCount);
                totalData.set(0, totalData.get(0) + diseaseGoalCount);

                // 처리된 질환 기록
                processedDiseases.add(groupKey);
            }

            // 기타 데이터 누적
            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            groupData.put("라벨링등록건수", (int) groupData.get("라벨링등록건수") + labelingCount);
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            groupData.put("라벨링pass건수", (int) groupData.get("라벨링pass건수") + firstCheck);
            totalData.set(2, totalData.get(2) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);
            totalData.set(4, totalData.get(4) + secondCheck);
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, Object> groupData = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(7, ""));
            subRow.set(0, groupKey); // 질환명
            subRow.set(1, groupData.get("목표건수").toString());
            subRow.set(2, groupData.get("라벨링등록건수").toString());
            subRow.set(3, groupData.get("라벨링pass건수").toString());

            int totalGroupGoalCount = (int) groupData.get("목표건수");
            int firstCheck = (int) groupData.get("라벨링pass건수");
            int secondCheck = (int) groupData.get("2차검수");

            // 1차 구축율 계산
            int firstBuildRate = (totalGroupGoalCount > 0) ? (firstCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(4, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int buildRateForGroup = (totalGroupGoalCount > 0) ? (secondCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(5, groupData.get("2차검수").toString());
            subRow.set(6, String.valueOf(buildRateForGroup));

            subData.add(subRow);
        }

        // subData 정렬
        subData.sort((a, b) -> {
            int indexA = diseaseOrder.indexOf(a.get(0)); // 질환명 기준 정렬
            int indexB = diseaseOrder.indexOf(b.get(0));
            return Integer.compare(indexA, indexB);
        });

        // totalData에서 전체 구축율 계산
        int totalGoalCount = totalData.get(0);
        if (totalGoalCount > 0) {
            totalData.set(3, (totalData.get(2) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(5, (totalData.get(4) * 100) / totalGoalCount); // 2차 구축율
        }

        // 결과 데이터에 추가
        institutionData.put("totalData", totalData);
        institutionData.put("subData", subData);

        log.info("Finished creating institution data for grouping key: {}", groupingKey);
        return institutionData;
    }


    public Map<String, Object> createDiseaseData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating disease data for grouping key: {}", groupingKey);

        Map<String, Object> diseaseData = new HashMap<>();
        diseaseData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(6, 0)); // 전체 데이터를 초기화
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 그룹화된 데이터 저장
        Map<String, Set<String>> processedDiseasesByInstitution = new HashMap<>(); // 기관별 처리된 질환 목록

        // 데이터 그룹화 및 처리
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 기관명
            String diseaseClass = (String) item.get("DISEASE_CLASS"); // 질환명
            String institutionId = (String) item.get("INSTITUTION_ID"); // 기관 ID

            // 그룹 데이터 초기화
            groupedDataMap.putIfAbsent(groupKey, new HashMap<>());
            Map<String, Object> groupData = groupedDataMap.get(groupKey);
            groupData.putIfAbsent(groupingKey, groupKey);
            groupData.putIfAbsent("목표건수", 0);
            groupData.putIfAbsent("라벨링등록건수", 0);
            groupData.putIfAbsent("라벨링pass건수", 0);
            groupData.putIfAbsent("2차검수", 0);

            // 기관별 처리된 질환 목록 관리
            processedDiseasesByInstitution.putIfAbsent(institutionId, new HashSet<>());
            Set<String> processedDiseases = processedDiseasesByInstitution.get(institutionId);

            // 중복 방지: 질환 목표 건수는 한 번만 추가
            if (!processedDiseases.contains(diseaseClass)) {
                int goalCount = institutionDiseaseGoals
                        .getOrDefault(institutionId, new HashMap<>())
                        .getOrDefault(diseaseClass, 0);

                groupData.put("목표건수", (int) groupData.get("목표건수") + goalCount);
                totalData.set(0, totalData.get(0) + goalCount);

                // 처리된 질환으로 기록
                processedDiseases.add(diseaseClass);
            }

            // 기타 항목 누적
            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            groupData.put("라벨링등록건수", (int) groupData.get("라벨링등록건수") + labelingCount);
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            groupData.put("라벨링pass건수", (int) groupData.get("라벨링pass건수") + firstCheck);
            totalData.set(2, totalData.get(2) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);
            totalData.set(4, totalData.get(4) + secondCheck);
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, Object> groupData = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(7, ""));
            subRow.set(0, groupKey); // 기관명
            subRow.set(1, groupData.get("목표건수").toString());
            subRow.set(2, groupData.get("라벨링등록건수").toString());
            subRow.set(3, groupData.get("라벨링pass건수").toString());

            int totalGroupGoalCount = (int) groupData.get("목표건수");
            int firstCheck = (int) groupData.get("라벨링pass건수");
            int secondCheck = (int) groupData.get("2차검수");

            // 1차 구축율 계산
            int firstBuildRate = (totalGroupGoalCount > 0) ? (firstCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(4, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int buildRateForGroup = (totalGroupGoalCount > 0) ? (secondCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(5, groupData.get("2차검수").toString());
            subRow.set(6, String.valueOf(buildRateForGroup));

            subData.add(subRow);
        }

        // subData 정렬 (기관명 기준)
        subData.sort(Comparator.comparing(subRow -> subRow.get(0)));

        // totalData에서 전체 구축율 계산
        int totalGoalCount = totalData.get(0);
        if (totalGoalCount > 0) {
            totalData.set(3, (totalData.get(2) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(5, (totalData.get(4) * 100) / totalGoalCount); // 2차 구축율
        }

        // 결과 데이터 저장
        diseaseData.put("totalData", totalData);
        diseaseData.put("subData", subData);

        log.info("Finished creating disease data for grouping key: {}", groupingKey);
        return diseaseData;
    }


}