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

    public List<Map<String, String>> processZipFile(MultipartFile file) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();

        // 압축 파일을 임시 디렉토리에 저장
        String tempDir = System.getProperty("java.io.tmpdir");
        Path tempZipFilePath = Paths.get(tempDir, file.getOriginalFilename());

        try {
            Files.write(tempZipFilePath, file.getBytes());

            // 압축 파일 풀기
            File extractedDir = unzip(tempZipFilePath.toString(), tempDir);

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

                                // 9번째 행부터 데이터 읽기
                                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // 9번째 행은 8번째 인덱스
                                    Row row = sheet.getRow(rowIndex);
                                    if (row != null) {
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
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            // 압축 파일 삭제
            try {
                Files.deleteIfExists(tempZipFilePath);
            } catch (IOException e) {
                e.printStackTrace();
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
