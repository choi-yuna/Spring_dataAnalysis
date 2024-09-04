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
        // 고유한 파일 ID 생성 (UUID 사용)
        String fileId = UUID.randomUUID().toString();

        // 파일 저장할 경로 설정 (예: tmpdir 사용, 필요에 따라 변경 가능)
        String tempDir = System.getProperty("java.io.tmpdir");
        Path filePath = Paths.get(tempDir, fileId + "_" + file.getOriginalFilename());

        // 파일 저장
        Files.write(filePath, file.getBytes());

        // 파일 ID와 경로를 저장
        fileStorage.put(fileId, filePath);

        // 고유한 파일 ID를 반환
        return fileId;
    }

    // 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, String>> analyzeData(String fileId, String diseaseClass, int institutionId) throws IOException {
        // 파일 ID로 저장된 파일 경로를 찾음
        Path filePath = fileStorage.get(fileId);

        if (filePath == null) {
            throw new IOException("파일을 찾을 수 없습니다: " + fileId);
        }

        // 파일 경로를 기반으로 데이터를 분석하는 로직 구현
        return processZipFile(new File(filePath.toString()), diseaseClass, institutionId);
    }

    // ZIP 파일 처리 및 필터링 메소드 (파일 경로 기반)
    public List<Map<String, String>> processZipFile(File file, String diseaseClass, int institutionId) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();

        // ZIP 파일 풀기
        File extractedDir = unzip(file.getPath(), file.getParent());

        // 압축 해제된 디렉토리에서 엑셀 파일 읽기
        File[] excelFiles = extractedDir.listFiles((dir, name) -> name.endsWith(".xlsx"));

        if (excelFiles != null) {
            for (File excelFile : excelFiles) {
                try (InputStream inputStream = new FileInputStream(excelFile);
                     Workbook workbook = new XSSFWorkbook(inputStream)) {

                    int numberOfSheets = workbook.getNumberOfSheets();

                    for (int i = 0; i < numberOfSheets; i++) {
                        Sheet sheet = workbook.getSheetAt(i);
                        String sheetName = sheet.getSheetName().trim(); // 시트 이름의 앞뒤 공백 제거

                        // 해당 시트에 대한 헤더 목록을 가져옴
                        List<String> expectedHeaders = SheetHeaderMapping.getHeadersForSheet(sheetName);

                        if (expectedHeaders != null) {  // 매핑된 헤더가 있는 경우에만 처리

                            // 4번째 행을 헤더로 설정
                            Row headerRow = sheet.getRow(3); // 4번째 행은 3번째 인덱스
                            if (headerRow == null) {
                                throw new RuntimeException("헤더 행이 존재하지 않습니다. 파일을 확인해주세요.");
                            }

                            // 실제 엑셀 파일의 헤더를 읽어옴
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

                            // 질환과 기관을 나타내는 컬럼의 인덱스 확인
                            Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                            Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                            if (diseaseClassIndex == null || institutionIdIndex == null) {
                                continue;
                            }

                            // 9번째 행부터 데이터 읽기
                            for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                                Row row = sheet.getRow(rowIndex);
                                if (row != null) {
                                    // 필터링을 위한 질환 및 기관 값 가져오기
                                    String diseaseClassValue = getCellValueAsString(row.getCell(diseaseClassIndex));
                                    String institutionIdValueStr = getCellValueAsString(row.getCell(institutionIdIndex));

                                    // institutionIdValueStr이 비어 있지 않은지 확인
                                    if (!institutionIdValueStr.isEmpty()) {
                                        try {
                                            int institutionIdValue = Integer.parseInt(institutionIdValueStr);

                                            // 조건에 맞는 행만 추가 (질환 및 기관 필터링)
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
                                                // rowData에 실제 데이터가 있는지 확인하고, 데이터가 있으면 리스트에 추가
                                                if (!rowData.isEmpty()) {
                                                    dataList.add(rowData);
                                                }
                                            }
                                        } catch (NumberFormatException e) {
                                            // institutionIdValueStr이 숫자로 변환할 수 없는 경우 처리 (예: 로그 기록)
                                            System.err.println("숫자로 변환할 수 없는 institutionId 값: "
                                                    + institutionIdValueStr + " (파일: " + excelFile.getName() + ", 시트: " + sheetName + ")");

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

        // 인코딩 문제 해결을 위해 다양한 인코딩 시도
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath), Charset.forName("Cp437"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String filePath;
                try {
                    filePath = Paths.get(destDir, entry.getName()).toString();
                } catch (Exception e) {
                    System.err.println("파일 이름 처리 중 오류 발생: " + e.getMessage());
                    continue; // 문제가 있는 파일 이름은 무시하고 다음 파일로 넘어감
                }

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
        if (cell ==null) {
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
