package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataGropedService {

    private static final Map<String, Map<String, Integer>> institutionDiseaseGoals = new HashMap<>();
    private static final List<String> diseaseOrder = Arrays.asList("치주질환", "골수염","구강암", "두개안면");

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
        institutionDiseaseGoals.put("국립암센터", nationalCancerCenter);

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

        // 초기화: 모든 질환별로 목표 건수를 설정
        for (String diseaseClass : diseaseOrder) {
            groupedData.putIfAbsent(diseaseClass, new HashMap<>());
            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            diseaseData.putIfAbsent("title", diseaseClass);
            diseaseData.putIfAbsent("totalData", new ArrayList<>(Collections.nCopies(7, 0))); // 초기값 설정
            diseaseData.putIfAbsent("subData", new ArrayList<>());

            // 모든 기관별로 목표 건수 초기화
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            for (Map.Entry<String, Map<String, Integer>> institutionEntry : institutionDiseaseGoals.entrySet()) {
                String institutionId = institutionEntry.getKey();
                int institutionGoalCount = institutionEntry.getValue().getOrDefault(diseaseClass, 0);

                // 목표 건수가 0인 기관은 제외
                if (institutionGoalCount == 0) continue;

                // 기관별 초기 데이터 설정
                List<String> subRow = new ArrayList<>();
                subRow.add(institutionId); // 기관명
                subRow.add(String.valueOf(institutionGoalCount)); // 목표 건수
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subData.add(subRow);

                // 총 목표 건수 업데이트
                List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
                totalData.set(0, totalData.get(0) + institutionGoalCount); // 목표 건수 합산
            }
        }

        // 데이터가 있는 경우 결과를 그룹화
        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");
            if (diseaseClass == null || institutionId == null) continue;

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);

            // 총합 데이터 업데이트
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(3, totalData.get(3) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(5, totalData.get(5) + secondCheck);

            // 기관 데이터 업데이트
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            for (List<String> subRow : subData) {
                if (subRow.get(0).equals(institutionId)) {
                    subRow.set(2, String.valueOf(Integer.parseInt(subRow.get(2)) + labelingCount)); // 라벨링 등록건수
                    subRow.set(4, String.valueOf(Integer.parseInt(subRow.get(4)) + firstCheck)); // 라벨링 pass건수
                    subRow.set(6, String.valueOf(Integer.parseInt(subRow.get(6)) + secondCheck)); // 2차 검수
                    break;
                }
            }
        }

        // 구축율 계산
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            // 목표 건수가 0인 기관 제거
            subData.removeIf(subRow -> Integer.parseInt(subRow.get(1)) == 0);

            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int LabellingCheck = Integer.parseInt(subRow.get(2));
                int firstCheck = Integer.parseInt(subRow.get(4));
                int secondCheck = Integer.parseInt(subRow.get(6));

                int LabellingRate = (institutionGoalCount > 0) ? (int) ((LabellingCheck / (double) institutionGoalCount) * 100) : 0;
                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                subRow.set(3, String.valueOf(LabellingRate)); // 1차 구축율
                subRow.set(5, String.valueOf(firstCheckRate)); // 1차 구축율
                subRow.set(7, String.valueOf(secondCheckRate)); // 2차 구축율
            }

            // 총합 데이터 구축율 계산
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int totalGoalCount = totalData.get(0);
            int totalLabellingCheck = totalData.get(1);
            int totalFirstCheck = totalData.get(3);
            int totalSecondCheck = totalData.get(5);


            int totalLabellingRate = (totalGoalCount > 0) ? (int) ((totalLabellingCheck / (double) totalGoalCount) * 100) : 0;
            int totalFirstCheckRate = (totalGoalCount > 0) ? (int) ((totalFirstCheck / (double) totalGoalCount) * 100) : 0;
            int totalSecondCheckRate = (totalGoalCount > 0) ? (int) ((totalSecondCheck / (double) totalGoalCount) * 100) : 0;

            totalData.set(2, totalLabellingRate); // 1차 구축율
            totalData.set(4, totalFirstCheckRate); // 1차 구축율
            totalData.set(6, totalSecondCheckRate); // 2차 구축율
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

        // 초기화: 모든 기관별로 질환별 목표 건수를 설정
        for (Map.Entry<String, Map<String, Integer>> institutionEntry : institutionDiseaseGoals.entrySet()) {
            String institutionId = institutionEntry.getKey();
            groupedData.putIfAbsent(institutionId, new HashMap<>());
            Map<String, Object> institutionData = groupedData.get(institutionId);
            institutionData.putIfAbsent("title", institutionId);
            institutionData.putIfAbsent("totalData", new ArrayList<>(Collections.nCopies(7, 0))); // 초기값 설정
            institutionData.putIfAbsent("subData", new ArrayList<>());

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");

            // 모든 질환에 대해 초기 데이터 설정
            for (Map.Entry<String, Integer> diseaseEntry : institutionEntry.getValue().entrySet()) {
                String diseaseClass = diseaseEntry.getKey();
                int goalCount = diseaseEntry.getValue();

                // 목표 건수가 0인 질환은 제외
                if (goalCount == 0) continue;

                // 질환별 초기 데이터 설정
                List<String> subRow = new ArrayList<>();
                subRow.add(diseaseClass); // 질환명
                subRow.add(String.valueOf(goalCount)); // 목표 건수
                subRow.add("0"); // 라벨링 건수
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subRow.add("0");
                subData.add(subRow);

                // 총 목표 건수 업데이트
                List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
                totalData.set(0, totalData.get(0) + goalCount); // 목표 건수 합산
            }
        }

        // 데이터가 있는 경우 결과를 그룹화
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            if (institutionId == null || diseaseClass == null) continue;

            Map<String, Object> institutionData = groupedData.get(institutionId);

            // 총합 데이터 업데이트
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(3, totalData.get(3) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(5, totalData.get(5) + secondCheck);

            // subData 업데이트
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            for (List<String> subRow : subData) {
                if (subRow.get(0).equals(diseaseClass)) {
                    subRow.set(2, String.valueOf(Integer.parseInt(subRow.get(2)) + labelingCount)); // 라벨링 건수
                    subRow.set(4, String.valueOf(Integer.parseInt(subRow.get(4)) + firstCheck)); // pass 건수
                    subRow.set(6, String.valueOf(Integer.parseInt(subRow.get(6)) + secondCheck)); // 2차 검수
                    break;
                }
            }
        }

        // 각 기관별로 subData (질환별) 정렬 및 구축율 계산
        for (Map<String, Object> institutionData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");

            // 질환별 정렬
            subData.sort(Comparator.comparing(subRow -> diseaseOrder.indexOf(subRow.get(0))));

            for (List<String> subRow : subData) {
                int goalCount = Integer.parseInt(subRow.get(1));
                int labellingCheck = Integer.parseInt(subRow.get(2));
                int firstCheck = Integer.parseInt(subRow.get(4));
                int secondCheck = Integer.parseInt(subRow.get(6));

                // 구축율 계산
                int labellingCheckRate = (goalCount > 0) ? (int) ((labellingCheck / (double) goalCount) * 100) : 0;
                int firstCheckRate = (goalCount > 0) ? (int) ((firstCheck / (double) goalCount) * 100) : 0;
                int secondCheckRate = (goalCount > 0) ? (int) ((secondCheck / (double) goalCount) * 100) : 0;

                subRow.set(3, String.valueOf(labellingCheckRate)); // 라벨링 구축율
                subRow.set(5, String.valueOf(firstCheckRate)); // 1차 구축율
                subRow.set(7, String.valueOf(secondCheckRate)); // 2차 구축율
            }

            // 총합 데이터 구축율 계산
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            int totalGoalCount = totalData.get(0);
            int totalLabellingCheck = totalData.get(1);
            int totalFirstCheck = totalData.get(3);
            int totalSecondCheck = totalData.get(5);


            int totalFirstCheckRate = (totalGoalCount > 0) ? (int) ((totalFirstCheck / (double) totalGoalCount) * 100) : 0;
            int totalLabellingCheckRate = (totalGoalCount > 0) ? (int) ((totalLabellingCheck / (double) totalGoalCount) * 100) : 0;
            int totalSecondCheckRate = (totalGoalCount > 0) ? (int) ((totalSecondCheck / (double) totalGoalCount) * 100) : 0;

            totalData.set(2, totalLabellingCheckRate); // 라벨링 구축율
            totalData.set(4, totalFirstCheckRate); // 1차 구축율
            totalData.set(6, totalSecondCheckRate); // 2차 구축율
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

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(7, 0)); // 초기화된 totalData
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 그룹화된 질환 데이터

        // 모든 질환 초기화
        for (String disease : diseaseOrder) {
            groupedDataMap.putIfAbsent(disease, new HashMap<>());
            Map<String, Object> groupData = groupedDataMap.get(disease);
            groupData.put(groupingKey, disease);

            // 목표건수 초기화: 모든 기관의 질환 목표 합계
            int totalDiseaseGoal = institutionDiseaseGoals.values().stream()
                    .mapToInt(diseaseMap -> diseaseMap.getOrDefault(disease, 0))
                    .sum();

            groupData.put("목표건수", totalDiseaseGoal);
            groupData.put("라벨링등록건수", 0);
            groupData.put("라벨링pass건수", 0);
            groupData.put("2차검수", 0);

            // 목표건수 누적
            totalData.set(0, totalData.get(0) + totalDiseaseGoal);
        }

        // 데이터 그룹화 및 추가 처리
        for (Map<String, Object> item : resultList) {
            String groupKey = (String) item.get(groupingKey); // 질환명

            // 데이터가 있는 경우, 기존 그룹 데이터에 값 누적
            if (groupedDataMap.containsKey(groupKey)) {
                Map<String, Object> groupData = groupedDataMap.get(groupKey);

                int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
                groupData.put("라벨링등록건수", (int) groupData.get("라벨링등록건수") + labelingCount);
                totalData.set(1, totalData.get(1) + labelingCount);

                int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
                groupData.put("라벨링pass건수", (int) groupData.get("라벨링pass건수") + firstCheck);
                totalData.set(3, totalData.get(3) + firstCheck);

                int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
                groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);
                totalData.set(5, totalData.get(5) + secondCheck);
            }
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, Object> groupData = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(8, ""));
            subRow.set(0, groupKey); // 질환명
            subRow.set(1, groupData.get("목표건수").toString());
            subRow.set(2, groupData.get("라벨링등록건수").toString());
            subRow.set(4, groupData.get("라벨링pass건수").toString());
            subRow.set(6, groupData.get("2차검수").toString());

            int totalGroupGoalCount = (int) groupData.get("목표건수");
            int labellingCheck = (int) groupData.get("라벨링등록건수");
            int firstCheck = (int) groupData.get("라벨링pass건수");
            int secondCheck = (int) groupData.get("2차검수");

            //라벨링 구축율 계산
            int labellingBuildRate = (totalGroupGoalCount > 0) ? (labellingCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(3, String.valueOf(labellingBuildRate));
            // 1차 구축율 계산
            int firstBuildRate = (totalGroupGoalCount > 0) ? (firstCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(5, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int buildRateForGroup = (totalGroupGoalCount > 0) ? (secondCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(7, String.valueOf(buildRateForGroup));

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
            totalData.set(2, (totalData.get(1) * 100) / totalGoalCount); // 라벨링 구축율
            totalData.set(4, (totalData.get(3) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(6, (totalData.get(5) * 100) / totalGoalCount); // 2차 구축율
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

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(7, 0)); // 전체 데이터를 초기화
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 기관별 데이터 저장

        // 기관별로 목표건수 초기화
        for (Map.Entry<String, Map<String, Integer>> institutionEntry : institutionDiseaseGoals.entrySet()) {
            String institutionId = institutionEntry.getKey();
            Map<String, Integer> diseaseGoals = institutionEntry.getValue();

            groupedDataMap.putIfAbsent(institutionId, new HashMap<>());
            Map<String, Object> institutionDataMap = groupedDataMap.get(institutionId);
            institutionDataMap.put("기관명", institutionId);
            institutionDataMap.put("목표건수", 0);
            institutionDataMap.put("라벨링등록건수", 0);
            institutionDataMap.put("라벨링pass건수", 0);
            institutionDataMap.put("2차검수", 0);

            // 목표건수 누적
            int totalGoalForInstitution = diseaseGoals.values().stream().mapToInt(Integer::intValue).sum();
            institutionDataMap.put("목표건수", totalGoalForInstitution);
            totalData.set(0, totalData.get(0) + totalGoalForInstitution);
        }

        // 결과 데이터 누적 처리
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get(groupingKey); // 기관명
            if (institutionId == null || !groupedDataMap.containsKey(institutionId)) {
                continue; // 기관이 없는 경우 건너뜀
            }

            Map<String, Object> institutionDataMap = groupedDataMap.get(institutionId);

            // 기타 항목 누적
            int labelingCount = (item.get("라벨링등록건수") != null) ? (int) item.get("라벨링등록건수") : 0;
            institutionDataMap.put("라벨링등록건수", (int) institutionDataMap.get("라벨링등록건수") + labelingCount);
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            institutionDataMap.put("라벨링pass건수", (int) institutionDataMap.get("라벨링pass건수") + firstCheck);
            totalData.set(3, totalData.get(3) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            institutionDataMap.put("2차검수", (int) institutionDataMap.get("2차검수") + secondCheck);
            totalData.set(5, totalData.get(5) + secondCheck);
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String institutionId = entry.getKey();
            Map<String, Object> institutionDataMap = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(8, ""));
            subRow.set(0, institutionId); // 기관명
            subRow.set(1, institutionDataMap.get("목표건수").toString());
            subRow.set(2, institutionDataMap.get("라벨링등록건수").toString());
            subRow.set(4, institutionDataMap.get("라벨링pass건수").toString());
            subRow.set(6, institutionDataMap.get("2차검수").toString());

            int totalGoalForInstitution = (int) institutionDataMap.get("목표건수");
            int labellingCheck = (int) institutionDataMap.get("라벨링등록건수");
            int firstCheck = (int) institutionDataMap.get("라벨링pass건수");
            int secondCheck = (int) institutionDataMap.get("2차검수");

            //라벨링 구축율 계산
            int labellingBuildRate = (totalGoalForInstitution > 0) ? (labellingCheck * 100 / totalGoalForInstitution) : 0;
            subRow.set(3, String.valueOf(labellingBuildRate));

            // 1차 구축율 계산
            int firstBuildRate = (totalGoalForInstitution > 0) ? (firstCheck * 100 / totalGoalForInstitution) : 0;
            subRow.set(5, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int secondBuildRate = (totalGoalForInstitution > 0) ? (secondCheck * 100 / totalGoalForInstitution) : 0;
            subRow.set(7, String.valueOf(secondBuildRate));

            subData.add(subRow);
        }

        // subData 정렬 (기관명 기준)
        subData.sort(Comparator.comparing(subRow -> subRow.get(0)));

        // totalData에서 전체 구축율 계산
        int totalGoalCount = totalData.get(0);
        if (totalGoalCount > 0) {
            totalData.set(2, (totalData.get(1) * 100) / totalGoalCount); // 라벨링 구축율
            totalData.set(4, (totalData.get(3) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(6, (totalData.get(5) * 100) / totalGoalCount); // 2차 구축율
        }

        // 결과 데이터 저장
        diseaseData.put("totalData", totalData);
        diseaseData.put("subData", subData);

        log.info("Finished creating disease data for grouping key: {}", groupingKey);
        return diseaseData;
    }

}