package com.fas.dentistry_data_analysis.service.dashBoard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TotalDataGropedService {

    private static final Map<String, Map<String, Integer>> institutionDiseaseGoals = new HashMap<>();
    private static final Map<String, Map<String, Integer>> controlGroupDiseaseGoals = new HashMap<>();
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


        Map<String, Integer> DKUGroup1 = new HashMap<>();
        DKUGroup1.put("(질환군)", 808);
        DKUGroup1.put("(대조군)", 570);
        controlGroupDiseaseGoals.put("단국대학교", DKUGroup1);

        Map<String, Integer> DKUGroup2 = new HashMap<>();
        DKUGroup2.put("(질환군)", 808);
        DKUGroup2.put("(대조군)", 570);
        controlGroupDiseaseGoals.put("골수염", DKUGroup2);
    }

    public List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        // 초기화: 모든 질환별로 목표 건수를 설정
        for (String diseaseClass : diseaseOrder) {
            groupedData.putIfAbsent(diseaseClass, new HashMap<>());
            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            diseaseData.putIfAbsent("title", diseaseClass);
            diseaseData.putIfAbsent("totalData", new ArrayList<>(Collections.nCopies(9, 0))); // 초기값 설정
            diseaseData.putIfAbsent("subData", new ArrayList<>());

            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            List<List<String>> controlData = null;

            // "골수염" 질환에 대해서만 controlData 추가
            if (diseaseClass.equals("골수염")) {
                controlData = new ArrayList<>();
                diseaseData.put("controlData", controlData);
            }

            for (Map.Entry<String, Map<String, Integer>> institutionEntry : institutionDiseaseGoals.entrySet()) {
                String institutionId = institutionEntry.getKey();
                int institutionGoalCount = institutionEntry.getValue().getOrDefault(diseaseClass, 0);

                if (institutionGoalCount > 0) {
                    // subData에 데이터 추가 (모든 기관 데이터 포함)
                    List<String> subRow = new ArrayList<>();
                    subRow.add(institutionId); // 기관명
                    subRow.add(String.valueOf(institutionGoalCount)); // 목표 건수
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subData.add(subRow);
                }

                // "골수염"에 대한 단국대학교 controlData 추가
                if (diseaseClass.equals("골수염") && institutionId.equals("단국대학교")) {
                    for (Map.Entry<String, Integer> controlGroupEntry : controlGroupDiseaseGoals.getOrDefault(institutionId, new HashMap<>()).entrySet()) {
                        String controlGroupName = controlGroupEntry.getKey(); // "단국대학교 (대조군)" 또는 "단국대학교 (질환군)"
                        int controlGroupGoalCount = controlGroupEntry.getValue();

                        if (controlGroupGoalCount > 0) {
                            List<String> controlRow = new ArrayList<>();
                            controlRow.add(controlGroupName);
                            controlRow.add(null);
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlData.add(controlRow);
                        }
                    }
                }

                // 총 목표 건수 업데이트
                List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
                totalData.set(0, totalData.get(0) + institutionGoalCount); // 목표 건수 합산
            }
        }

        // 데이터가 있는 경우 결과를 그룹화
        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");
            String groupType = (String) item.get("GROUP_TYPE");
            if (diseaseClass == null || institutionId == null) continue;

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            List<List<String>> controlData = diseaseClass.equals("골수염") ?
                    (List<List<String>>) diseaseData.get("controlData") : null;

            // 총합 데이터 업데이트
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int dcmCount = (item.get("영상") != null) ? (int) item.get("영상") : 0;
            totalData.set(1, totalData.get(1) + dcmCount);

            int crfCount = (item.get("임상") != null) ? (int) item.get("임상") : 0;
            totalData.set(2, totalData.get(2) + crfCount);


            int jsonCount = (item.get("메타") != null) ? (int) item.get("메타") : 0;
            totalData.set(3, totalData.get(3) + jsonCount);

            int drawingCount = (item.get("drawing") != null) ? (int) item.get("drawing") : 0;
            totalData.set(4, totalData.get(4) + drawingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(5, totalData.get(5) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(7, totalData.get(7) + secondCheck);

            // subData 업데이트 (모든 데이터 추가)
            for (List<String> subRow : subData) {
                if (subRow.get(0).equals(institutionId)) {
                    subRow.set(2, String.valueOf(Integer.parseInt(subRow.get(2)) + dcmCount));
                    subRow.set(3, String.valueOf(Integer.parseInt(subRow.get(3)) + crfCount));
                    subRow.set(4, String.valueOf(Integer.parseInt(subRow.get(4)) + jsonCount));
                    subRow.set(5, String.valueOf(Integer.parseInt(subRow.get(5)) + drawingCount));
                    subRow.set(6, String.valueOf(Integer.parseInt(subRow.get(6)) + firstCheck)); // 라벨링 pass건수
                    subRow.set(8, String.valueOf(Integer.parseInt(subRow.get(8)) + secondCheck)); // 2차 검수
                    break;
                }
            }

            // controlData 업데이트 ("골수염"에 대한 단국대학교 대조군/질환군 구분 저장)
            if (controlData != null && institutionId.equals("단국대학교")) {
                String controlGroupName = (groupType != null && groupType.equals("대조군")) ? "(대조군)" : "(질환군)";
                for (List<String> controlRow : controlData) {
                    if (controlRow.get(0).equals(controlGroupName)) {
                        controlRow.set(2, String.valueOf(Integer.parseInt(controlRow.get(2)) + dcmCount)); // 라벨링 등록건수
                        controlRow.set(3, String.valueOf(Integer.parseInt(controlRow.get(3)) + crfCount)); // 라벨링 등록건수
                        controlRow.set(4, String.valueOf(Integer.parseInt(controlRow.get(4)) + jsonCount)); // 라벨링 등록건수
                        controlRow.set(5, String.valueOf(Integer.parseInt(controlRow.get(5)) + drawingCount)); // 라벨링 등록건수
                        controlRow.set(6, String.valueOf(Integer.parseInt(controlRow.get(6)) + firstCheck)); // 라벨링 pass건수
                        controlRow.set(8, String.valueOf(Integer.parseInt(controlRow.get(8)) + secondCheck)); // 2차 검수
                        break;
                    }
                }
            }

        }
        // 구축율 계산
        for (Map<String, Object> diseaseData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            List<List<String>> controlData = diseaseData.containsKey("controlData") ?
                    (List<List<String>>) diseaseData.get("controlData") : null;

            // 목표 건수가 0인 기관 제거 (subData)
            subData.removeIf(subRow -> Integer.parseInt(subRow.get(1)) == 0);

            // subData 구축율 계산
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(6));
                int secondCheck = Integer.parseInt(subRow.get(8));

                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                subRow.set(7, String.valueOf(firstCheckRate)); // 1차 구축율
                subRow.set(9, String.valueOf(secondCheckRate)); // 2차 구축율
            }

            // controlData 구축율 계산
            if (controlData != null) {
                for (List<String> controlRow : controlData) {
                    //int controlGroupGoalCount = Integer.parseInt(controlRow.get(1));
                    int LabellingCheck = Integer.parseInt(controlRow.get(2));
                    int firstCheck = Integer.parseInt(controlRow.get(4));
                    int secondCheck = Integer.parseInt(controlRow.get(6));

                    //int LabellingRate = (controlGroupGoalCount > 0) ? (int) ((LabellingCheck / (double) controlGroupGoalCount) * 100) : 0;
                   // int firstCheckRate = (controlGroupGoalCount > 0) ? (int) ((firstCheck / (double) controlGroupGoalCount) * 100) : 0;
                    //int secondCheckRate = (controlGroupGoalCount > 0) ? (int) ((secondCheck / (double) controlGroupGoalCount) * 100) : 0;

                    controlRow.set(7, null); // 1차 구축율
                    controlRow.set(9, null); // 2차 구축율
                }
            }

            // 총합 데이터 구축율 계산
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            int totalGoalCount = totalData.get(0);
            int totalFirstCheck = totalData.get(5);
            int totalSecondCheck = totalData.get(7);

            int totalFirstCheckRate = (totalGoalCount > 0) ? (int) ((totalFirstCheck / (double) totalGoalCount) * 100) : 0;
            int totalSecondCheckRate = (totalGoalCount > 0) ? (int) ((totalSecondCheck / (double) totalGoalCount) * 100) : 0;

            totalData.set(6, totalFirstCheckRate); // 1차 구축율
            totalData.set(8, totalSecondCheckRate); // 2차 구축율
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
            institutionData.putIfAbsent("totalData", new ArrayList<>(Collections.nCopies(9, 0))); // 초기값 설정
            institutionData.putIfAbsent("subData", new ArrayList<>());

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            List<List<String>> controlData = null;

            // 단국대학교인 경우에만 controlData 추가
            if (institutionId.equals("단국대학교")) {
                controlData = new ArrayList<>();
                institutionData.put("controlData", controlData);
            }

            // 모든 질환에 대해 초기 데이터 설정
            for (Map.Entry<String, Integer> diseaseEntry : institutionEntry.getValue().entrySet()) {
                String diseaseClass = diseaseEntry.getKey();
                int goalCount = diseaseEntry.getValue();

                if (goalCount > 0) {
                    // subData에 데이터 추가
                    List<String> subRow = new ArrayList<>();
                    subRow.add(diseaseClass); // 질환명
                    subRow.add(String.valueOf(goalCount)); // 목표 건수
                    subRow.add("0"); // 라벨링 건수
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subRow.add("0");
                    subData.add(subRow);
                }

                // 단국대학교의 "골수염" 데이터 처리
                if (institutionId.equals("단국대학교") && diseaseClass.equals("골수염")) {
                    for (Map.Entry<String, Integer> controlGroupEntry : controlGroupDiseaseGoals.getOrDefault(diseaseClass, new HashMap<>()).entrySet()) {
                        String controlGroupName = controlGroupEntry.getKey(); // "골수염(대조군)" 또는 "골수염(질환군)"
                        int controlGroupGoalCount = controlGroupEntry.getValue();

                        if (controlGroupGoalCount > 0) {
                            List<String> controlRow = new ArrayList<>();
                            controlRow.add(controlGroupName);
                            controlRow.add(null);
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlRow.add("0");
                            controlData.add(controlRow);
                        }
                    }
                }

                // 총 목표 건수 업데이트
                List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
                totalData.set(0, totalData.get(0) + goalCount); // 목표 건수 합산
            }
        }

        // 데이터가 있는 경우 결과를 그룹화
        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("INSTITUTION_ID");
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String groupType = (String) item.get("GROUP_TYPE");
            if (institutionId == null || diseaseClass == null) continue;

            Map<String, Object> institutionData = groupedData.get(institutionId);
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            List<List<String>> controlData = institutionId.equals("단국대학교") ?
                    (List<List<String>>) institutionData.get("controlData") : null;

            // 총합 데이터 업데이트
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            int dcmCount = (item.get("영상") != null) ? (int) item.get("영상") : 0;
            totalData.set(1, totalData.get(1) + dcmCount);

            int crfCount = (item.get("임상") != null) ? (int) item.get("임상") : 0;
            totalData.set(2, totalData.get(2) + crfCount);


            int jsonCount = (item.get("메타") != null) ? (int) item.get("메타") : 0;
            totalData.set(3, totalData.get(3) + jsonCount);

            int drawingCount = (item.get("drawing") != null) ? (int) item.get("drawing") : 0;
            totalData.set(4, totalData.get(4) + drawingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            totalData.set(5, totalData.get(5) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(7, totalData.get(7) + secondCheck);

            // subData 업데이트 (모든 데이터 추가)
            for (List<String> subRow : subData) {
                if (subRow.get(0).equals(diseaseClass)) {
                    subRow.set(2, String.valueOf(Integer.parseInt(subRow.get(2)) + dcmCount));
                    subRow.set(3, String.valueOf(Integer.parseInt(subRow.get(3)) + crfCount));
                    subRow.set(4, String.valueOf(Integer.parseInt(subRow.get(4)) + jsonCount));
                    subRow.set(5, String.valueOf(Integer.parseInt(subRow.get(5)) + drawingCount));
                    subRow.set(6, String.valueOf(Integer.parseInt(subRow.get(6)) + firstCheck)); // 라벨링 pass건수
                    subRow.set(8, String.valueOf(Integer.parseInt(subRow.get(8)) + secondCheck)); // 2차 검수
                    break;
                }
            }

            // controlData 업데이트 ("골수염"의 단국대학교 대조군/질환군 구분 저장)
            if (controlData != null && diseaseClass.equals("골수염")) {
                String controlGroupName = (groupType != null && groupType.equals("대조군")) ? "(대조군)" : "(질환군)";
                for (List<String> controlRow : controlData) {
                    if (controlRow.get(0).equals(controlGroupName)) {
                        controlRow.set(2, String.valueOf(Integer.parseInt(controlRow.get(2)) + dcmCount));
                        controlRow.set(3, String.valueOf(Integer.parseInt(controlRow.get(3)) + crfCount));
                        controlRow.set(4, String.valueOf(Integer.parseInt(controlRow.get(4)) + jsonCount)); // 라벨링 등록건수
                        controlRow.set(5, String.valueOf(Integer.parseInt(controlRow.get(5)) + drawingCount)); // 라벨링 등록건수
                        controlRow.set(6, String.valueOf(Integer.parseInt(controlRow.get(6)) + firstCheck)); // 라벨링 pass건수
                        controlRow.set(8, String.valueOf(Integer.parseInt(controlRow.get(8)) + secondCheck)); // 2차 검수
                        break;
                    }
                }
            }
        }
        // 각 기관별로 subData 및 controlData 구축율 계산
        for (Map<String, Object> institutionData : groupedData.values()) {
            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");

            // subData 정렬 및 구축율 계산
            subData.sort(Comparator.comparing(subRow -> diseaseOrder.indexOf(subRow.get(0))));
            for (List<String> subRow : subData) {
                int institutionGoalCount = Integer.parseInt(subRow.get(1));
                int firstCheck = Integer.parseInt(subRow.get(6));
                int secondCheck = Integer.parseInt(subRow.get(8));

                int firstCheckRate = (institutionGoalCount > 0) ? (int) ((firstCheck / (double) institutionGoalCount) * 100) : 0;
                int secondCheckRate = (institutionGoalCount > 0) ? (int) ((secondCheck / (double) institutionGoalCount) * 100) : 0;

                subRow.set(7, String.valueOf(firstCheckRate)); // 1차 구축율
                subRow.set(9, String.valueOf(secondCheckRate)); // 2차 구축율
            }

            // 총합 데이터 구축율 계산
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            int totalGoalCount = totalData.get(0);
            int totalFirstCheck = totalData.get(5);
            int totalSecondCheck = totalData.get(7);

            int totalFirstCheckRate = (totalGoalCount > 0) ? (int) ((totalFirstCheck / (double) totalGoalCount) * 100) : 0;
            int totalSecondCheckRate = (totalGoalCount > 0) ? (int) ((totalSecondCheck / (double) totalGoalCount) * 100) : 0;

            totalData.set(6, totalFirstCheckRate); // 1차 구축율
            totalData.set(8, totalSecondCheckRate); // 2차 구축율

            // controlData 구축율 계산 (단국대학교의 골수염 데이터만 처리)
            if (institutionData.containsKey("controlData")) {
                List<List<String>> controlData = (List<List<String>>) institutionData.get("controlData");
                for (List<String> controlRow : controlData) {
                    //int controlGoalCount = Integer.parseInt(controlRow.get(1));
                    int controlLabellingCheck = Integer.parseInt(controlRow.get(2));
                    int controlFirstCheck = Integer.parseInt(controlRow.get(4));
                    int controlSecondCheck = Integer.parseInt(controlRow.get(6));

                    // 구축율 계산
                   // int controlLabellingRate = (controlGoalCount > 0) ? (int) ((controlLabellingCheck / (double) controlGoalCount) * 100) : 0;
                   // int controlFirstRate = (controlGoalCount > 0) ? (int) ((controlFirstCheck / (double) controlGoalCount) * 100) : 0;
                   // int controlSecondRate = (controlGoalCount > 0) ? (int) ((controlSecondCheck / (double) controlGoalCount) * 100) : 0;

                    controlRow.set(7, null); // 1차 구축율
                    controlRow.set(9, null); // 2차 구축율
                }
            }
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

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(9, 0)); // 초기화된 totalData
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
            groupData.put("영상", 0);
            groupData.put("임상", 0);
            groupData.put("메타", 0);
            groupData.put("drawing", 0);
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

                int dcmCount = (item.get("영상") != null) ? (int) item.get("영상") : 0;
                groupData.put("영상", (int) groupData.get("영상") + dcmCount);
                totalData.set(1, totalData.get(1) + dcmCount);

                int crfCount = (item.get("임상") != null) ? (int) item.get("임상") : 0;
                groupData.put("임상", (int) groupData.get("임상") + crfCount);
                totalData.set(2, totalData.get(2) + crfCount);


                int jsonCount = (item.get("메타") != null) ? (int) item.get("메타") : 0;
                groupData.put("메타", (int) groupData.get("메타") + jsonCount);
                totalData.set(3, totalData.get(3) + jsonCount);

                int drawingCount = (item.get("drawing") != null) ? (int) item.get("drawing") : 0;
                groupData.put("drawing", (int) groupData.get("drawing") + drawingCount);
                totalData.set(4, totalData.get(4) + drawingCount);

                int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
                groupData.put("라벨링pass건수", (int) groupData.get("라벨링pass건수") + firstCheck);
                totalData.set(5, totalData.get(5) + firstCheck);

                int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
                groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);
                totalData.set(7, totalData.get(7) + secondCheck);
            }
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, Object> groupData = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(10, ""));
            subRow.set(0, groupKey); // 질환명
            subRow.set(1, groupData.get("목표건수").toString());
            subRow.set(2, groupData.get("영상").toString());
            subRow.set(3, groupData.get("임상").toString());
            subRow.set(4, groupData.get("메타").toString());
            subRow.set(5, groupData.get("drawing").toString());
            subRow.set(6, groupData.get("라벨링pass건수").toString());
            subRow.set(8, groupData.get("2차검수").toString());

            int totalGroupGoalCount = (int) groupData.get("목표건수");
            int firstCheck = (int) groupData.get("라벨링pass건수");
            int secondCheck = (int) groupData.get("2차검수");


            // 1차 구축율 계산
            int firstBuildRate = (totalGroupGoalCount > 0) ? (firstCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(7, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int buildRateForGroup = (totalGroupGoalCount > 0) ? (secondCheck * 100 / totalGroupGoalCount) : 0;
            subRow.set(9, String.valueOf(buildRateForGroup));

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
            totalData.set(6, (totalData.get(5) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(8, (totalData.get(7) * 100) / totalGoalCount); // 2차 구축율
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

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(9, 0)); // 전체 데이터를 초기화
        Map<String, Map<String, Object>> groupedDataMap = new HashMap<>(); // 기관별 데이터 저장

        // 기관별로 목표건수 초기화
        for (Map.Entry<String, Map<String, Integer>> institutionEntry : institutionDiseaseGoals.entrySet()) {
            String institutionId = institutionEntry.getKey();
            Map<String, Integer> diseaseGoals = institutionEntry.getValue();

            groupedDataMap.putIfAbsent(institutionId, new HashMap<>());
            Map<String, Object> institutionDataMap = groupedDataMap.get(institutionId);
            institutionDataMap.put("기관명", institutionId);
            institutionDataMap.put("목표건수", 0);
            institutionDataMap.put("영상", 0);
            institutionDataMap.put("임상", 0);
            institutionDataMap.put("메타", 0);
            institutionDataMap.put("drawing", 0);
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

            int dcmCount = (item.get("영상") != null) ? (int) item.get("영상") : 0;
            institutionDataMap.put("영상", (int) institutionDataMap.get("영상") + dcmCount);
            totalData.set(1, totalData.get(1) + dcmCount);

            int crfCount = (item.get("임상") != null) ? (int) item.get("임상") : 0;
            institutionDataMap.put("임상", (int) institutionDataMap.get("임상") + crfCount);
            totalData.set(2, totalData.get(2) + crfCount);


            int jsonCount = (item.get("메타") != null) ? (int) item.get("메타") : 0;
            institutionDataMap.put("메타", (int) institutionDataMap.get("메타") + jsonCount);
            totalData.set(3, totalData.get(3) + jsonCount);

            int drawingCount = (item.get("drawing") != null) ? (int) item.get("drawing") : 0;
            institutionDataMap.put("drawing", (int) institutionDataMap.get("drawing") + drawingCount);
            totalData.set(4, totalData.get(4) + drawingCount);

            int firstCheck = (item.get("라벨링pass건수") != null) ? (int) item.get("라벨링pass건수") : 0;
            institutionDataMap.put("라벨링pass건수", (int) institutionDataMap.get("라벨링pass건수") + firstCheck);
            totalData.set(5, totalData.get(5) + firstCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            institutionDataMap.put("2차검수", (int) institutionDataMap.get("2차검수") + secondCheck);
            totalData.set(7, totalData.get(7) + secondCheck);
        }

        // subData 생성
        List<List<String>> subData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : groupedDataMap.entrySet()) {
            String institutionId = entry.getKey();
            Map<String, Object> institutionDataMap = entry.getValue();

            List<String> subRow = new ArrayList<>(Collections.nCopies(10, ""));
            subRow.set(0, institutionId); // 기관명
            subRow.set(1, institutionDataMap.get("목표건수").toString());
            subRow.set(2, institutionDataMap.get("영상").toString());
            subRow.set(3, institutionDataMap.get("임상").toString());
            subRow.set(4, institutionDataMap.get("메타").toString());
            subRow.set(5, institutionDataMap.get("drawing").toString());
            subRow.set(6, institutionDataMap.get("라벨링pass건수").toString());
            subRow.set(8, institutionDataMap.get("2차검수").toString());

            int totalGoalForInstitution = (int) institutionDataMap.get("목표건수");
            int firstCheck = (int) institutionDataMap.get("라벨링pass건수");
            int secondCheck = (int) institutionDataMap.get("2차검수");



            // 1차 구축율 계산
            int firstBuildRate = (totalGoalForInstitution > 0) ? (firstCheck * 100 / totalGoalForInstitution) : 0;
            subRow.set(7, String.valueOf(firstBuildRate));

            // 2차 구축율 계산
            int secondBuildRate = (totalGoalForInstitution > 0) ? (secondCheck * 100 / totalGoalForInstitution) : 0;
            subRow.set(9, String.valueOf(secondBuildRate));

            subData.add(subRow);
        }

        // subData 정렬 (기관명 기준)
        subData.sort(Comparator.comparing(subRow -> subRow.get(0)));

        // totalData에서 전체 구축율 계산
        int totalGoalCount = totalData.get(0);
        if (totalGoalCount > 0) {
            totalData.set(6, (totalData.get(5) * 100) / totalGoalCount); // 1차 구축율
            totalData.set(8, (totalData.get(7) * 100) / totalGoalCount); // 2차 구축율
        }

        // 결과 데이터 저장
        diseaseData.put("totalData", totalData);
        diseaseData.put("subData", subData);

        log.info("Finished creating disease data for grouping key: {}", groupingKey);
        return diseaseData;
    }

}