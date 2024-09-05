package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.config.SheetHeaderMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ExcelUploadService {

    // 파일 ID와 파일 경로를 매핑하는 Map
    private final Map<String, Path> fileStorage = new HashMap<>();

    // 파일을 서버에 저장하고 고유한 파일 ID를 반환하는 메소드
    public String storeFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String tempDir = System.getProperty("java.io.tmpdir");
        Path filePath = Paths.get(tempDir, fileId + "_" + file.getOriginalFilename());

        // 파일 저장
        Files.write(filePath, file.getBytes());

        // 파일 ID와 경로를 저장
        fileStorage.put(fileId, filePath);

        return fileId; // 고유한 파일 ID 반환
    }

    // 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, String>> analyzeData(String fileId, String diseaseClass, int institutionId) throws IOException {
        Path filePath = fileStorage.get(fileId);
        if (filePath == null) {
            throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
        }

        // 파일의 확장자를 확인하여 ZIP 또는 엑셀 파일로 처리
        return processFile(new File(filePath.toString()), diseaseClass, institutionId);
    }

    // 파일 확장자를 기반으로 ZIP 또는 엑셀 파일 처리
    public List<Map<String, String>> processFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".zip")) {
            return processZipFile(file, diseaseClass, institutionId);
        } else if (fileName.endsWith(".xlsx")) {
            return processExcelFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    // ZIP 파일 처리 및 필터링 메소드
    public List<Map<String, String>> processZipFile(File file, String diseaseClass, int institutionId) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();
        File extractedDir = unzip(file.getPath(), file.getParent());
        File[] excelFiles = extractedDir.listFiles((dir, name) -> name.endsWith(".xlsx"));

        if (excelFiles != null) {
            for (File excelFile : excelFiles) {
                dataList.addAll(processExcelFile(excelFile, diseaseClass, institutionId));
            }
        }
        return dataList;
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
                                        // 잘못된 숫자 값 처리: 빈 값으로 설정
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
        }
        return dataList;
    }

    private File unzip(String zipFilePath, String destDir) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) dir.mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath), Charset.forName("Cp437"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String filePath = Paths.get(destDir, entry.getName()).toString();

                if (!entry.isDirectory()) {
                    extractFile(zis, filePath);
                } else {
                    File dirEntry = new File(filePath);
                    dirEntry.mkdirs();
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("ZIP 파일 내 파일 이름 처리 중 오류 발생: " + e.getMessage(), e);
        }
        return new File(destDir);
    }

    private void extractFile(ZipInputStream zis, String filePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[4096];
            int read;
            while ((read = zis.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

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



