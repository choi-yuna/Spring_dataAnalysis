package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fas.dentistry_data_analysis.util.ValueMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnalyzeBoardServiceImpl {

    // 폴더 경로에서 xlsx 파일을 찾아서 처리하는 메소드
    public Map<String, Object> processFilesInFolder(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("폴더 경로가 유효하지 않거나 폴더가 아닙니다.");
        }

        // 결과를 저장할 리스트 (기관과 질환을 포함한 모든 항목이 리스트에 저장됩니다)
        List<Map<String, Object>> resultList = new ArrayList<>();

        // 폴더 내 모든 .xlsx 파일을 재귀적으로 찾고 처리
        processFolderRecursively(folder, resultList);

        // 질환별 데이터와 기관별 데이터를 각각 처리
        Map<String, Object> response = new HashMap<>();

        // 질환별 데이터 처리
        List<Map<String, Object>> diseaseData = groupDataByDisease(resultList);
        diseaseData.add(createAllData(resultList, "질환", "질환 ALL"));  // 질환ALL 데이터 추가
        response.put("질환별", diseaseData);

        // 기관별 데이터 처리
        List<Map<String, Object>> institutionData = groupDataByInstitution(resultList);
        institutionData.add(createAllData(resultList, "기관", "기관 ALL"));  // 기관ALL 데이터 추가
        response.put("기관별", institutionData);

        return response;
    }

    // 질환별로 데이터를 그룹화하는 메소드
    private List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("질환");
            String institutionId = (String) item.get("기관");

            if (!groupedData.containsKey(diseaseClass)) {
                groupedData.put(diseaseClass, new HashMap<>());
            }

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            if (!diseaseData.containsKey("title")) {
                diseaseData.put("title", diseaseClass);
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(7, 0))); // 초기값 설정
                diseaseData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");
            totalData.set(0, totalData.get(0) + (int) item.get("목표건수"));
            totalData.set(1, totalData.get(1) + (int) item.get("라벨링건수"));
            totalData.set(2, totalData.get(2) + (int) item.get("1차검수"));
            totalData.set(3, totalData.get(3) + (int) item.get("데이터구성검수"));
            totalData.set(4, totalData.get(4) + (int) item.get("2차검수"));

            // 구축율 계산: (2차검수 / 목표건수) * 100
            int secondCheck = (int) item.get("2차검수");
            int goalCount = (int) item.get("목표건수");

            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(6, (int) buildRate); // 구축율을 totalData의 7번째 항목에 넣기
            }

            // subData에 각 기관 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(institutionId);
            subRow.add(item.get("목표건수").toString());
            subRow.add(item.get("라벨링건수").toString());
            subRow.add(item.get("1차검수").toString());
            subRow.add(item.get("데이터구성검수").toString());
            subRow.add(item.get("2차검수").toString());

            // 43% 대신 실제 구축율 값 추가
            int buildRate = (int) totalData.get(6);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            subData.add(subRow);
        }

        return formatGroupedData(groupedData);
    }

    // 기관별로 데이터를 그룹화하는 메소드
    private List<Map<String, Object>> groupDataByInstitution(List<Map<String, Object>> resultList) {
        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("기관");
            String diseaseClass = (String) item.get("질환");

            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
            }

            Map<String, Object> institutionData = groupedData.get(institutionId);
            if (!institutionData.containsKey("title")) {
                institutionData.put("title",institutionId);
                institutionData.put("totalData", new ArrayList<>(Collections.nCopies(7, 0))); // 초기값 설정
                institutionData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");
            totalData.set(0, totalData.get(0) + (int) item.get("목표건수"));
            totalData.set(1, totalData.get(1) + (int) item.get("라벨링건수"));
            totalData.set(2, totalData.get(2) + (int) item.get("1차검수"));
            totalData.set(3, totalData.get(3) + (int) item.get("데이터구성검수"));
            totalData.set(4, totalData.get(4) + (int) item.get("2차검수"));

            // 구축율 계산: (2차검수 / 목표건수) * 100
            int secondCheck = (int) item.get("2차검수");
            int goalCount = (int) item.get("목표건수");

            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(6, (int) buildRate); // 구축율을 totalData의 7번째 항목에 넣기
            }

            // subData에 각 질환 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass);
            subRow.add(item.get("목표건수").toString());
            subRow.add(item.get("라벨링건수").toString());
            subRow.add(item.get("1차검수").toString());
            subRow.add(item.get("데이터구성검수").toString());
            subRow.add(item.get("2차검수").toString());

            // 43% 대신 실제 구축율 값 추가
            int buildRate = (int) totalData.get(6);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            subData.add(subRow);
        }

        return formatGroupedData(groupedData);
    }

    // 질환 ALL 또는 기관 ALL 데이터를 생성하는 통합 메소드
    private Map<String, Object> createAllData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        Map<String, Object> allData = new HashMap<>();
        allData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(7, 0));
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
            groupData.put("목표건수", (int) groupData.get("목표건수") + (int) item.get("목표건수"));
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + (int) item.get("라벨링건수"));
            groupData.put("1차검수", (int) groupData.get("1차검수") + (int) item.get("1차검수"));
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + (int) item.get("데이터구성검수"));
            groupData.put("2차검수", (int) groupData.get("2차검수") + (int) item.get("2차검수"));

            // 총합 데이터 누적
            totalData.set(0, totalData.get(0) + (int) item.get("목표건수"));
            totalData.set(1, totalData.get(1) + (int) item.get("라벨링건수"));
            totalData.set(2, totalData.get(2) + (int) item.get("1차검수"));
            totalData.set(3, totalData.get(3) + (int) item.get("데이터구성검수"));
            totalData.set(4, totalData.get(4) + (int) item.get("2차검수"));
        }

        // 구축율 계산: (2차검수 / 목표건수) * 100
        int secondCheck = totalData.get(4);
        int goalCount = totalData.get(0);

        if (goalCount > 0) {
            double buildRate = (double) secondCheck / goalCount * 100;
            totalData.set(6, (int) buildRate); // 구축율을 totalData의 7번째 항목에 넣기
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

            int buildRateForGroup = (int) groupData.get("2차검수") * 100 / (int) groupData.get("목표건수");
            subRow.add(buildRateForGroup + ""); // 구축율을 추가 (백분율)

            subData.add(subRow);
        }

        allData.put("subData", subData);

        return allData;
    }

    // 그룹화된 데이터를 형식에 맞게 변환하는 메소드
    private List<Map<String, Object>> formatGroupedData(Map<String, Map<String, Object>> groupedData) {
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

    // 폴더를 재귀적으로 탐색하는 메소드
    private void processFolderRecursively(File folder, List<Map<String, Object>> resultList) throws IOException {
        // 기존 내용 그대로 유지
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
        if (files != null) {
            for (File file : files) {
                List<Map<String, String>> filteredData = processFile(file);
                Set<String> processedImageIds = new HashSet<>();
                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");
                    String diseaseClass = rowData.get("DISEASE_CLASS");
                    String institutionId = rowData.get("INSTITUTION_ID");

                    if (!processedImageIds.contains(imageId)) {
                        processedImageIds.add(imageId);
                        incrementStatus(resultList, institutionId, diseaseClass, "목표건수");
                    }
                }

                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");
                    String diseaseClass = rowData.get("DISEASE_CLASS");
                    String institutionId = rowData.get("INSTITUTION_ID");

                    boolean dcmExists = checkFileExistsInSubfolders(folder, imageId + ".dcm");
                    boolean jsonExists = checkFileExistsInSubfolders(folder, imageId + ".json");
                    boolean iniExists = checkFileExistsInSubfolders(folder, imageId + ".ini");

                    if (!(dcmExists && jsonExists && iniExists)) {
                        incrementStatus(resultList, institutionId, diseaseClass, "데이터구성검수");
                    }

                    File jsonFile = getFileInSubfolders(folder, imageId + ".json");
                    if (jsonFile != null) {
                        int labelingStatus = getJsonStatus(jsonFile, "Labeling_Info", "완료");
                        int firstCheckStatus = getJsonStatus(jsonFile, "First_Check_Info", "2");
                        int secondCheckStatus = getJsonStatus(jsonFile, "Second_Check_Info", "2");

                        if (labelingStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "라벨링건수");
                        if (firstCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "1차검수");
                        if (secondCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "2차검수");
                    }
                }
            }
        }

        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                processFolderRecursively(subFolder, resultList);
            }
        }
    }

private void incrementStatus(List<Map<String, Object>> resultList,
                                 String institutionId, String diseaseClass, String status) {
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> institutionId.equals(item.get("기관")) && diseaseClass.equals(item.get("질환")))
                .findFirst();

        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("기관", institutionId);
            newEntry.put("질환", diseaseClass);
            newEntry.put("2차검수", 0);
            newEntry.put("1차검수", 0);
            newEntry.put("목표건수", 0);
            newEntry.put("데이터구성검수", 0);
            newEntry.put("라벨링건수", 0);
            resultList.add(newEntry);
            existing = Optional.of(newEntry);
        }

        Map<String, Object> statusMap = existing.get();
        statusMap.put(status, (int) statusMap.get(status) + 1);
    }

    private boolean checkFileExistsInSubfolders(File folder, String fileName) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));

        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    return true;
                }
            }
        }

        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                if (checkFileExistsInSubfolders(subFolder, fileName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private File getFileInSubfolders(File folder, String fileName) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));

        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    return file;
                }
            }
        }

        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                File foundFile = getFileInSubfolders(subFolder, fileName);
                if (foundFile != null) {
                    return foundFile;
                }
            }
        }

        return null;
    }

    private int getJsonStatus(File jsonFile, String key, String targetValue) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFile);
            JsonNode infoNode = rootNode.get(key);

            if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
                JsonNode firstElement = infoNode.get(0);

                if (key.equals("Labeling_Info")) {
                    JsonNode labelingStatusNode = firstElement.get("Labelling");
                    if (labelingStatusNode != null && labelingStatusNode.asText().equals("완료")) {
                        return 2;
                    }
                }

                if (key.equals("First_Check_Info")) {
                    JsonNode checkResultNode = firstElement.get("Checking1");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;
                    }
                }

                if (key.equals("Second_Check_Info")) {
                    JsonNode checkResultNode = firstElement.get("Checking2");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private List<Map<String, String>> processFile(File excelFile) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();

        try (FileInputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
                    continue;
                }

                Row headerRow = sheet.getRow(3);
                if (headerRow == null) {
                    continue;
                }

                Map<String, Integer> headerIndexMap = new HashMap<>();
                for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                    Cell cell = headerRow.getCell(cellIndex);
                    if (cell != null) {
                        String headerName = cell.getStringCellValue().trim();
                        headerIndexMap.put(headerName, cellIndex);
                    }
                }

                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        Map<String, String> rowData = new LinkedHashMap<>();

                        Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                        Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                        Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                        if (imageIdIndex != null) {
                            Cell imageIdCell = row.getCell(imageIdIndex);
                            String imageIdValue = (imageIdCell != null) ? ExcelUtils.getCellValueAsString(imageIdCell): "";
                            rowData.put("IMAGE_ID", imageIdValue);
                        }
                        if (diseaseClassIndex != null) {
                            Cell diseaseClassCell = row.getCell(diseaseClassIndex);
                            String diseaseClassValue = (diseaseClassCell != null) ? ExcelUtils.getCellValueAsString(diseaseClassCell): "";
                            String mappedDiseaseClass = ValueMapping.getDiseaseClass(diseaseClassValue);
                            rowData.put("DISEASE_CLASS", mappedDiseaseClass);

                        }
                        if (institutionIdIndex != null) {
                            Cell institutionIdCell = row.getCell(institutionIdIndex);
                            String institutionIdValue = (institutionIdCell != null) ? ExcelUtils.getCellValueAsString(institutionIdCell) : "";
                            String mappedInstitutionId = ValueMapping.getInstitutionDescription(institutionIdValue);
                            rowData.put("INSTITUTION_ID", mappedInstitutionId);
                        }

                        if (!rowData.isEmpty()) {
                            filteredData.add(rowData);
                        }
                    }
                }
            }
        }
        return filteredData;
    }
}
