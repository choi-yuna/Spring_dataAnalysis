package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.util.ExcelUtils;
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
    public List<Map<String, Object>> processFilesInFolder(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("폴더 경로가 유효하지 않거나 폴더가 아닙니다.");
        }

        // 결과를 저장할 리스트 (기관과 질환을 포함한 모든 항목이 리스트에 저장됩니다)
        List<Map<String, Object>> resultList = new ArrayList<>();

        // 폴더 내 모든 .xlsx 파일을 재귀적으로 찾고 처리
        processFolderRecursively(folder, resultList);

        return resultList;
    }

    // 폴더를 재귀적으로 탐색하는 메소드
    private void processFolderRecursively(File folder, List<Map<String, Object>> resultList) throws IOException {
        // 폴더 내의 모든 .xlsx 파일을 찾기
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (files != null) {
            for (File file : files) {
                // 엑셀 파일 처리
                List<Map<String, String>> filteredData = processFile(file);

                // 이미지 ID를 추적하여 중복을 방지
                Set<String> processedImageIds = new HashSet<>();

                // 이미지 ID에 대해 전처리
                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");
                    String diseaseClass = rowData.get("DISEASE_CLASS");
                    String institutionId = rowData.get("INSTITUTION_ID");

                    // '목표건수' 카운트 (파일명이 중복되지 않게 처리)
                    if (!processedImageIds.contains(imageId)) {
                        processedImageIds.add(imageId);
                        incrementStatus(resultList, institutionId, diseaseClass, "목표건수");
                    }
                }

                // 각 `imageId`에 대해 필요한 파일이 존재하는지 확인하고 데이터구성검수 카운트
                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");
                    String diseaseClass = rowData.get("DISEASE_CLASS");
                    String institutionId = rowData.get("INSTITUTION_ID");

                    // dcm, json, ini 파일 존재 여부 확인 (하위 폴더 포함)
                    boolean dcmExists = checkFileExistsInSubfolders(folder, imageId + ".dcm");
                    boolean jsonExists = checkFileExistsInSubfolders(folder, imageId + ".json");
                    boolean iniExists = checkFileExistsInSubfolders(folder, imageId + ".ini");

                    // 누락된 파일이 있으면 데이터 구성 검수 로 카운트
                    if (!(dcmExists && jsonExists && iniExists)) {
                        incrementStatus(resultList, institutionId, diseaseClass, "데이터구성검수");
                    }

                    // JSON 파일 파싱하여 상태 체크
                    File jsonFile = getFileInSubfolders(folder, imageId + ".json");
                    if (jsonFile != null) {
                        // JSON 상태값 추출
                        int labelingStatus = getJsonStatus(jsonFile, "Labeling_Info", "완료");
                        int firstCheckStatus = getJsonStatus(jsonFile, "First_Check_Info", "2");
                        int secondCheckStatus = getJsonStatus(jsonFile, "Second_Check_Info", "2");

                        // 상태 값 집계
                        if (labelingStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "라벨링건수");
                        if (firstCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "1차검수");
                        if (secondCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "2차검수");
                    }
                }
            }
        }

        // 하위 폴더가 있는 경우, 재귀적으로 탐색
        File[] subFolders = folder.listFiles(File::isDirectory);  // 디렉토리만 필터링
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                processFolderRecursively(subFolder, resultList);  // 하위 폴더 재귀 호출
            }
        }
    }

    // 상태 값을 집계하는 메소드
    private void incrementStatus(List<Map<String, Object>> resultList,
                                 String institutionId, String diseaseClass, String status) {
        // 이미 결과 리스트에 해당 기관과 질환이 존재하는지 확인
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> institutionId.equals(item.get("기관")) && diseaseClass.equals(item.get("질환")))
                .findFirst();

        // 해당 기관과 질환이 없으면 새로 추가
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

        // 해당 항목을 찾아서 값 증가
        Map<String, Object> statusMap = existing.get();
        statusMap.put(status, (int) statusMap.get(status) + 1);
    }

    // 하위 폴더까지 포함하여 파일을 찾는 메소드
    private boolean checkFileExistsInSubfolders(File folder, String fileName) {
        // 해당 폴더 내에서 파일을 찾음
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));

        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    return true;  // 해당 파일이 존재하면 true 리턴
                }
            }
        }

        // 하위 폴더가 있으면 재귀적으로 탐색
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                if (checkFileExistsInSubfolders(subFolder, fileName)) {
                    return true;  // 하위 폴더에서 파일을 찾으면 true 리턴
                }
            }
        }

        return false;  // 해당 파일이 없으면 false 리턴
    }

    // 하위 폴더에서 파일을 찾아 반환하는 메소드
    private File getFileInSubfolders(File folder, String fileName) {
        // 해당 폴더 내에서 파일을 찾음
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));

        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    return file;  // 해당 파일이 존재하면 리턴
                }
            }
        }

        // 하위 폴더가 있으면 재귀적으로 탐색
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                File foundFile = getFileInSubfolders(subFolder, fileName);
                if (foundFile != null) {
                    return foundFile;  // 하위 폴더에서 파일을 찾으면 리턴
                }
            }
        }

        return null;  // 해당 파일이 없으면 null 리턴
    }

    // JSON 파일에서 상태 값을 추출하는 메소드
    private int getJsonStatus(File jsonFile, String key, String targetValue) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFile);
            JsonNode infoNode = rootNode.get(key);

            if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
                JsonNode firstElement = infoNode.get(0);  // 첫 번째 요소

                if (key.equals("Labeling_Info")) {
                    JsonNode labelingStatusNode = firstElement.get("Labelling");
                    if (labelingStatusNode != null && labelingStatusNode.asText().equals("완료")) {
                        return 2;  // 완료 상태
                    }
                }

                if (key.equals("First_Check_Info")) {
                    JsonNode checkResultNode = firstElement.get("Checking1");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;  // 완료 상태
                    }
                }

                if (key.equals("Second_Check_Info")) {
                    JsonNode checkResultNode = firstElement.get("Checking2");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;  // 완료 상태
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;  // 상태 값이 없거나 오류가 발생하면 기본값인 0을 반환
    }

    // 엑셀 파일에서 IMAGE_ID 값을 추출하는 메소드
    private List<Map<String, String>> processFile(File excelFile) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();

        try (FileInputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
                    continue; // "CRF"가 없는 Sheet는 건너뛰기
                }

                Row headerRow = sheet.getRow(3); // 4번째 행을 헤더로 가정
                if (headerRow == null) {
                    continue;
                }

                // 헤더 행의 인덱스 매핑
                Map<String, Integer> headerIndexMap = new HashMap<>();
                for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                    Cell cell = headerRow.getCell(cellIndex);
                    if (cell != null) {
                        String headerName = cell.getStringCellValue().trim();
                        headerIndexMap.put(headerName, cellIndex);  // 모든 헤더 저장
                    }
                }

                // "IMAGE_ID" 값을 추출
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        Map<String, String> rowData = new LinkedHashMap<>();

                        // "IMAGE_ID", "DISEASE_CLASS", "INSTITUTION_ID" 필드 처리
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
                            rowData.put("DISEASE_CLASS", diseaseClassValue);
                        }
                        if (institutionIdIndex != null) {
                            Cell institutionIdCell = row.getCell(institutionIdIndex);
                            String institutionIdValue = (institutionIdCell != null) ? ExcelUtils.getCellValueAsString(institutionIdCell) : "";
                            rowData.put("INSTITUTION_ID", institutionIdValue);
                        }

                        // "IMAGE_ID"만 있으면 filteredData에 추가
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
