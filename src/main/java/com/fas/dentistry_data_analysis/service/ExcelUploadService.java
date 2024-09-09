package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.config.SheetHeaderMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
