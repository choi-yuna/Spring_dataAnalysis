package com.fas.dentistry_data_analysis.service;

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
    public Map<String, Integer> processFilesInFolder(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("폴더 경로가 유효하지 않거나 폴더가 아닙니다.");
        }

        // 결과를 저장할 맵
        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("전체파일", 0);
        resultMap.put("오류파일", 0);
        resultMap.put("라벨링", 0);
        resultMap.put("1차검수", 0);
        resultMap.put("2차검수", 0);

        // 폴더 내 모든 .xlsx 파일을 재귀적으로 찾고 처리
        processFolderRecursively(folder, resultMap);

        return resultMap;
    }

    // 폴더를 재귀적으로 탐색하는 메소드
    private void processFolderRecursively(File folder, Map<String, Integer> resultMap) throws IOException {
        // 폴더 내의 모든 .xlsx 파일을 찾기
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (files != null) {
            for (File file : files) {
                // 엑셀 파일 처리
                List<Map<String, String>> filteredData = processFile(file);

                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");

                    // dcm, json, ini 파일 존재 여부 확인 (하위 폴더 포함)
                    boolean dcmExists = checkFileExistsInSubfolders(folder, imageId + ".dcm");
                    boolean jsonExists = checkFileExistsInSubfolders(folder, imageId + ".json");
                    boolean iniExists = checkFileExistsInSubfolders(folder, imageId + ".ini");

                    System.out.println("확인 중: " + imageId);
                    System.out.println("dcm 파일 존재 여부: " + dcmExists);
                    System.out.println("json 파일 존재 여부: " + jsonExists);
                    System.out.println("ini 파일 존재 여부: " + iniExists);

                    if (dcmExists && jsonExists && iniExists) {
                        // 모든 파일이 존재하면 "전체파일" 갯수 증가
                        resultMap.put("전체파일", resultMap.get("전체파일") + 1);

                        // JSON 파일 파싱하여 상태 체크
                        File jsonFile = getFileInSubfolders(folder, imageId + ".json");
                        if (jsonFile != null) {
                            // 0번째 인덱스를 확인하여 완료 상태 체크
                            int labelingStatus = getJsonStatus(jsonFile, "Labeling_Info", "완료");
                            int firstCheckStatus = getJsonStatus(jsonFile, "First_Check_Info", "2");
                            int secondCheckStatus = getJsonStatus(jsonFile, "Second_Check_Info", "2");

                            if (labelingStatus == 2) {
                                resultMap.put("라벨링", resultMap.get("라벨링") + 1);
                            }
                            if (firstCheckStatus == 2) {
                                resultMap.put("1차검수", resultMap.get("1차검수") + 1);
                            }
                            if (secondCheckStatus == 2) {
                                resultMap.put("2차검수", resultMap.get("2차검수") + 1);
                            }
                        }
                    } else {
                        // 하나라도 누락된 파일이 있으면 "오류파일" 갯수 증가
                        resultMap.put("오류파일", resultMap.get("오류파일") + 1);
                    }
                }
            }
        }

        // 하위 폴더가 있는 경우, 재귀적으로 탐색
        File[] subFolders = folder.listFiles(File::isDirectory);  // 디렉토리만 필터링
        if (subFolders != null) {
            System.out.println("하위 폴더들:");
            for (File subFolder : subFolders) {
                System.out.println("하위 폴더 경로: " + subFolder.getAbsolutePath());  // 하위 폴더 경로 출력
                processFolderRecursively(subFolder, resultMap);  // 하위 폴더 재귀 호출
            }
        }
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

            // 해당 키의 값이 배열인지 확인하고 배열의 첫 번째 요소를 가져옵니다.
            if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
                JsonNode firstElement = infoNode.get(0);  // 첫 번째 요소

                // Labeling_Info의 경우
                if (key.equals("Labeling_Info")) {
                    JsonNode labelingStatusNode = firstElement.get("Labelling");
                    if (labelingStatusNode != null && labelingStatusNode.asText().equals("완료")) {
                        return 2;  // 완료 상태
                    }
                }

                // First_Check_Info의 경우
                if (key.equals("First_Check_Info")) {
                    JsonNode checkResultNode = firstElement.get("Checking1");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;  // 완료 상태
                    }
                }

                // Second_Check_Info의 경우
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

                        // "IMAGE_ID" 필드 처리 (이미지 코드 관련 데이터만 추출)
                        Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                        if (imageIdIndex != null) {
                            Cell imageIdCell = row.getCell(imageIdIndex);
                            String imageIdValue = (imageIdCell != null) ? imageIdCell.toString().trim() : "";
                            rowData.put("IMAGE_ID", imageIdValue);
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

