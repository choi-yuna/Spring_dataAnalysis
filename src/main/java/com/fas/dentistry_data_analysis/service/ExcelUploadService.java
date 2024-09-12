package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.config.SheetHeaderMapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class ExcelUploadService {

    // 파일 ID와 파일 경로를 매핑하는 Map
    private final Map<String, Path> fileStorage = new HashMap<>();

    // 파일을 서버에 저장하고 고유한 파일 ID를 반환하는 메소드
    public String storeFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String tempDir = System.getProperty("java.io.tmpdir");
        // 파일명에 포함된 공백이나 특수 문자를 제거
        String sanitizedFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        Path filePath = Paths.get(tempDir, fileId + "_" + sanitizedFileName);

        try {
            // 파일 저장
            Files.write(filePath, file.getBytes());
            // 파일이 제대로 저장되었는지 확인하는 로그
            if (Files.exists(filePath)) {
                System.out.println("파일 저장 성공: " + filePath.toString());
            } else {
                System.out.println("파일 저장 실패: " + filePath.toString());
            }

            // 파일 ID와 경로를 저장
            fileStorage.put(fileId, filePath);
        } catch (IOException e) {
            System.err.println("파일 저장 중 오류 발생: " + e.getMessage());
            throw e;
        }

        return fileId; // 고유한 파일 ID 반환
    }

    // 다중 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, String>> analyzeData(String[] fileIds, String diseaseClass, int institutionId) throws IOException {
        // fileIds가 null인지 확인
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Map<String, String>> combinedData = new ArrayList<>();

        // 여러 파일 ID 처리
        for (String fileId : fileIds) {
            Path filePath = fileStorage.get(fileId);
            if (filePath == null) {
                throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
            }

            // 엑셀 파일 분석 처리
            List<Map<String, String>> fileData = processFile(new File(filePath.toString()), diseaseClass, institutionId);
            combinedData.addAll(fileData);
        }

        return combinedData; // 모든 파일에서 추출된 데이터를 반환
    }


    // 파일 확장자를 기반으로 엑셀 파일 처리
    public List<Map<String, String>> processFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            return processExcelFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    // 엑셀 파일 처리 및 필터링 메소드
    public List<Map<String, String>> processExcelFile(File excelFile, String diseaseClass, int institutionId) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName().trim();
                List<String> expectedHeaders = SheetHeaderMapping.getHeadersForSheet(sheetName);

                if (expectedHeaders != null) {  // 매핑된 헤더가 있는 경우에만 처리
                    Row headerRow = sheet.getRow(3); // 4번째 행을 헤더로 설정
                    if (headerRow == null) {
                        throw new RuntimeException("헤더 행이 존재하지 않습니다. 파일을 확인해주세요.");
                    }

                    // 엑셀 파일의 헤더를 읽어옴
                    Map<String, Integer> headerIndexMap = new HashMap<>();
                    for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                        Cell cell = headerRow.getCell(cellIndex);
                        if (cell != null) {
                            String headerName = cell.getStringCellValue().trim();
                            if (expectedHeaders.contains(headerName)) {
                                headerIndexMap.put(headerName, cellIndex);
                            }
                        }
                    }

                    Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                    Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");
                    int institutionIdValue;

                    if (diseaseClassIndex != null && institutionIdIndex != null) {
                        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                            Row row = sheet.getRow(rowIndex);
                            if (row != null) {
                                String diseaseClassValue = getCellValueAsString(row.getCell(diseaseClassIndex));
                                String institutionIdValueStr = getCellValueAsString(row.getCell(institutionIdIndex));

                                if (!institutionIdValueStr.isEmpty()) {
                                    try {
                                        institutionIdValue = Integer.parseInt(institutionIdValueStr);
                                    } catch (NumberFormatException e) {
                                        System.err.println("숫자로 변환할 수 없는 institutionId 값: " + institutionIdValueStr);
                                        institutionIdValue = -1; // 잘못된 값은 -1로 설정
                                    }

                                    if (diseaseClassValue.equals(diseaseClass) && institutionIdValue == institutionId) {
                                        Map<String, String> rowData = new LinkedHashMap<>();
                                        for (String header : expectedHeaders) {
                                            Integer cellIndex = headerIndexMap.get(header);
                                            if (cellIndex != null) {
                                                Cell cell = row.getCell(cellIndex);
                                                String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
                                                rowData.put(header, cellValue);
                                            }
                                        }
                                        if (!rowData.isEmpty()) {
                                            dataList.add(rowData);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("엑셀 파일 처리 중 오류 발생: " + e.getMessage());
            throw e;
        }
        return dataList;
    }

    // 동적 필터링을 위한 메소드 추가
    // 동적 필터링을 위한 메소드 추가
    public Map<String, List<Map<String, Object>>> analyzeDataWithFilters(String[] fileIds, Map<String, String> filterConditions, List<String> headers) throws IOException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        // 빈도수 저장을 위한 Map
        Map<String, List<Map<String, Object>>> frequencyMap = new HashMap<>();

        for (String fileId : fileIds) {
            Path filePath = fileStorage.get(fileId);
            if (filePath == null) {
                throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
            }

            // 필터링된 데이터를 가져옴
            List<Map<String, String>> fileData = processFileWithFilters(new File(filePath.toString()), filterConditions, headers);

            // 각 헤더 값에 대한 빈도수 계산 및 구조화
            for (Map<String, String> rowData : fileData) {
                for (String header : headers) {
                    String value = rowData.getOrDefault(header, "").trim();

                    if (!value.isEmpty()) {
                        // 클라이언트에서 온 값을 범위로 변환
                        String rangeValue = getRangeForHeader(header, value);

                        // 해당 헤더의 빈도수 리스트 초기화
                        frequencyMap.putIfAbsent(header, new ArrayList<>());

                        // 해당 헤더에 이미 동일한 값이 있는지 확인
                        List<Map<String, Object>> valueList = frequencyMap.get(header);
                        Optional<Map<String, Object>> existingEntry = valueList.stream()
                                .filter(entry -> entry.get("value").equals(rangeValue))
                                .findFirst();

                        if (existingEntry.isPresent()) {
                            // 이미 존재하는 값이면 count 증가
                            Map<String, Object> entry = existingEntry.get();
                            int currentCount = (int) entry.get("count");
                            entry.put("count", currentCount + 1);
                        } else {
                            // 새로운 값이면 리스트에 추가
                            Map<String, Object> newValueCountMap = new LinkedHashMap<>();
                            newValueCountMap.put("value", rangeValue); // 범위로 변환된 값
                            newValueCountMap.put("count", 1);
                            valueList.add(newValueCountMap);
                        }
                    }
                }
            }
        }

        return frequencyMap;  // 각 헤더별 값과 빈도수를 리스트로 반환
    }

    // 헤더에 따라 구간을 결정하는 메소드
    private String getRangeForHeader(String header, String value) {
        switch (header) {
            case "P_AGE":
                return getAgeRange(value);
            case "P_WEIGHT":
                return getWeightRange(value);
            case "P_HEIGHT":
                return getHeightRange(value);
            default:
                return value;  // 구간이 필요 없는 경우 원래 값을 반환
        }
    }

    // 나이 필터링 구간 설정
    private String getAgeRange(String value) {
        int age = Integer.parseInt(value);
        if (age < 40) {
            return "40미만";
        } else if (age >= 40 && age <= 50) {
            return "40-50";
        } else if (age >= 51 && age <= 60) {
            return "51-60";
        } else if (age >= 61 && age <= 70) {
            return "61-70";
        } else if (age >= 71 && age <= 80) {
            return "71-80";
        } else if (age >= 81 && age <= 90) {
            return "81-90";
        } else {
            return "91+";
        }
    }

    // 체중 필터링 구간 설정
    private String getWeightRange(String value) {
        int weight = Integer.parseInt(value);
        if (weight < 10) {
            return "0-9";
        } else if (weight >= 10 && weight <= 20) {
            return "10-20";
        } else if (weight >= 21 && weight <= 30) {
            return "21-30";
        } else if (weight >= 31 && weight <= 40) {
            return "31-40";
        } else if (weight >= 41 && weight <= 50) {
            return "41-50";
        } else if (weight >= 51 && weight <= 60) {
            return "51-60";
        } else if (weight >= 61 && weight <= 70) {
            return "61-70";
        } else if (weight >= 71 && weight <= 80) {
            return "71-80";
        } else if (weight >= 81 && weight <= 90) {
            return "81-90";
        } else {
            return "90+";
        }
    }

    // 키 필터링 구간 설정
    private String getHeightRange(String value) {
        int height = Integer.parseInt(value);
        if (height < 140) {
            return "140 미만";
        } else if (height >= 140 && height <= 150) {
            return "140-150";
        } else if (height >= 151 && height <= 160) {
            return "151-160";
        } else if (height >= 161 && height <= 170) {
            return "161-170";
        } else if (height >= 171 && height <= 180) {
            return "171-180";
        } else if (height >= 181 && height <= 190) {
            return "181-190";
        } else {
            return "190+";
        }
    }




    // 동적 필터링을 처리하는 메소드 (기존)
    private List<Map<String, String>> processFileWithFilters(File excelFile, Map<String, String> filterConditions, List<String> headers) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
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
                        // 요청된 헤더에 포함된 것만 매핑
                        headerIndexMap.put(headerName, cellIndex);
                    }
                }

                // 모든 데이터를 먼저 필터링
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row != null && matchesConditions(row, headerIndexMap, filterConditions)) {
                        Map<String, String> rowData = new LinkedHashMap<>();
                        // 필터링된 데이터에서 헤더에 맞는 값만 추출
                        for (String header : headers) {
                            Integer cellIndex = headerIndexMap.get(header);
                            if (cellIndex != null) {
                                Cell cell = row.getCell(cellIndex);
                                String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
                                rowData.put(header, cellValue);
                            }
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


    // 조건과 일치하는지 확인하는 메소드
    private boolean matchesConditions(Row row, Map<String, Integer> headerIndexMap, Map<String, String> filterConditions) {
        for (Map.Entry<String, String> condition : filterConditions.entrySet()) {
            String header = condition.getKey();
            String expectedValue = condition.getValue();
            Integer cellIndex = headerIndexMap.get(header);

            if (cellIndex != null) {
                String cellValue = getCellValueAsString(row.getCell(cellIndex));

                // 숫자 범위 조건의 경우, send 값에 맞는지 확인
                if (header.equals("P_AGE")) {
                    if (!matchesAgeCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else if (header.equals("P_WEIGHT")) {
                    if (!matchesWeightCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else if (header.equals("P_HEIGHT")) {
                    if (!matchesHeightCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else {
                    // 기본 문자열 비교
                    if (!cellValue.equals(expectedValue)) {
                        return false;
                    }
                }
            }
        }
        return true; // 모든 조건을 만족하면 true 반환
    }


    // 나이 필터링 로직 (P_AGE)
    // 나이 필터링 로직 (P_AGE)
    private boolean matchesAgeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int age = Integer.parseInt(actualValue);
        switch (expectedSendValue) {
            case "0": return age < 40;
            case "1": return age >= 40 && age <= 50;
            case "2": return age >= 51 && age <= 60;
            case "3": return age >= 61 && age <= 70;
            case "4": return age >= 71 && age <= 80;
            case "5": return age >= 81 && age <= 90;
            case "6": return age > 90;
            default: return false;
        }
    }

    // 체중 필터링 로직 (P_WEIGHT)
    private boolean matchesWeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int weight = Integer.parseInt(actualValue);
        switch (expectedSendValue) {
            case "0": return weight < 10;
            case "1": return weight >= 10 && weight <= 20;
            case "2": return weight >= 21 && weight <= 30;
            case "3": return weight >= 31 && weight <= 40;
            case "4": return weight >= 41 && weight <= 50;
            case "5": return weight >= 51 && weight <= 60;
            case "6": return weight >= 61 && weight <= 70;
            case "7": return weight >= 71 && weight <= 80;
            case "8": return weight >= 81 && weight <= 90;
            case "9": return weight > 90;
            default: return false;
        }
    }

    // 키 필터링 로직 (P_HEIGHT)
    private boolean matchesHeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int height = Integer.parseInt(actualValue);
        switch (expectedSendValue) {
            case "0": return height < 140;
            case "1": return height >= 141 && height <= 150;
            case "2": return height >= 151 && height <= 160;
            case "3": return height >= 161 && height <= 170;
            case "4": return height >= 171 && height <= 180;
            case "5": return height >= 181 && height <= 190;
            case "6": return height > 190;
            default: return false;
        }
    }



    // 셀 데이터를 String으로 변환하는 메소드
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
