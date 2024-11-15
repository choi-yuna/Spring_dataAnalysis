package com.fas.dentistry_data_analysis.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnalyzeBoardServiceImpl {

    private static final int THREAD_POOL_SIZE = 4;  // 스레드 풀의 크기 조정

    // 폴더 경로에서 xlsx 파일을 찾아서 처리하는 메소드
    public Map<String, Object> processFilesInFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("폴더 경로가 유효하지 않거나 폴더가 아닙니다.");
        }

        // 결과를 저장할 맵
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("전체파일", 0);
        resultMap.put("오류파일", 0);
        resultMap.put("라벨링", 0);
        resultMap.put("1차검수", 0);
        resultMap.put("2차검수", 0);

        // 질환별, 대학별 통계 맵
        resultMap.put("질환별", new HashMap<String, Map<String, Map<String, Integer>>>());
        resultMap.put("대학별", new HashMap<String, Map<String, Map<String, Integer>>>());

        // ExecutorService를 통해 병렬 작업을 수행
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // 폴더 내 모든 .xlsx 파일을 재귀적으로 찾고 처리
        List<File> files = getAllFiles(folder);
        int batchSize = 100;  // 한 번에 처리할 파일 개수
        for (int i = 0; i < files.size(); i += batchSize) {
            List<File> batchFiles = files.subList(i, Math.min(i + batchSize, files.size()));
            executorService.submit(() -> processBatchFiles(batchFiles, resultMap, folder));
        }

        executorService.shutdown();  // 스레드 풀 종료
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        return resultMap;
    }

    // 폴더 내 모든 파일을 리스트로 가져오는 메소드
    private List<File> getAllFiles(File folder) {
        List<File> fileList = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (files != null) {
            fileList.addAll(Arrays.asList(files));
        }

        // 하위 폴더 재귀 탐색
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                fileList.addAll(getAllFiles(subFolder));  // 하위 폴더의 파일 추가
            }
        }

        return fileList;
    }

    // 배치 파일 처리
    private void processBatchFiles(List<File> batchFiles, Map<String, Object> resultMap, File folder) {
        for (File file : batchFiles) {
            try {
                List<Map<String, String>> filteredData = processFile(file);

                // image_num 기준으로 전체파일 갯수 카운트
                int totalFiles = updateTotalFilesCount(filteredData);

                synchronized (resultMap) {
                    resultMap.put("전체파일", (Integer) resultMap.get("전체파일") + totalFiles);
                }

                for (Map<String, String> rowData : filteredData) {
                    String imageId = rowData.get("IMAGE_ID");

                    // dcm, json, ini 파일 존재 여부 확인 (하위 폴더 포함)
                    boolean dcmExists = checkFileExistsInSubfolders(folder, imageId + ".dcm");
                    boolean jsonExists = checkFileExistsInSubfolders(folder, imageId + ".json");
                    boolean iniExists = checkFileExistsInSubfolders(folder, imageId + ".ini");

                    if (dcmExists && jsonExists && iniExists) {
                        synchronized (resultMap) {

                            // JSON 파일 파싱하여 상태 체크
                            File jsonFile = getFileInSubfolders(folder, imageId + ".json");
                            if (jsonFile != null) {
                                int labelingStatus = getJsonStatus(jsonFile, "Labeling_Info", "완료");
                                int firstCheckStatus = getJsonStatus(jsonFile, "First_Check_Info", "2");
                                int secondCheckStatus = getJsonStatus(jsonFile, "Second_Check_Info", "2");

                                // 질환별, 대학별로 통계 추가
                                String diseaseClass = rowData.get("DISEASE_CLASS");
                                String institutionId = rowData.get("INSTITUTION_ID");

                                // 질환별 통계 업데이트
                                updateDiseaseStats(resultMap, diseaseClass, institutionId, labelingStatus, firstCheckStatus, secondCheckStatus);

                                // 대학별 통계 업데이트
                                updateInstitutionStats(resultMap, institutionId, diseaseClass, labelingStatus, firstCheckStatus, secondCheckStatus);
                            }
                        }
                    } else {
                        synchronized (resultMap) {
                            resultMap.put("오류파일", (Integer) resultMap.get("오류파일") + 1);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // image_num을 기준으로 전체파일 갯수 업데이트
    private int updateTotalFilesCount(List<Map<String, String>> filteredData) {
        int totalFiles = 0;
        for (Map<String, String> rowData : filteredData) {
            String imageNum = rowData.get("IMAGE_NUM");  // image_num이 어떤 필드에 있는지 확인
            if (imageNum != null && !imageNum.isEmpty()) {
                totalFiles++;
            }
        }
        return totalFiles;
    }

    // 통합된 통계 업데이트 메소드
    private void updateStats(Map<String, Object> resultMap, String key, String groupKey, String subKey, int labelingStatus, int firstCheckStatus, int secondCheckStatus) {
        // resultMap에서 질환별 또는 대학별 통계를 가져옵니다.
        Map<String, Map<String, Map<String, Integer>>> statsMap = (Map<String, Map<String, Map<String, Integer>>>) resultMap.get(key);

        // 해당 그룹(질환명 또는 대학명)별 통계 맵 가져오기 (없으면 새로 생성)
        Map<String, Map<String, Integer>> groupMap = statsMap.getOrDefault(groupKey, new HashMap<>()); // 질환명 또는 대학명

        // 해당 서브키(기관명 또는 질환명)별 통계 맵 가져오기 (없으면 새로 생성)
        Map<String, Integer> fileStats = groupMap.getOrDefault(subKey, new HashMap<>());  // 기관명 또는 질환명

        // 파일 통계 업데이트
        fileStats.put("전체파일", fileStats.getOrDefault("전체파일", 0) + 1);
        if (labelingStatus == 2) fileStats.put("라벨링", fileStats.getOrDefault("라벨링", 0) + 1);
        if (firstCheckStatus == 2) fileStats.put("1차검수", fileStats.getOrDefault("1차검수", 0) + 1);
        if (secondCheckStatus == 2) fileStats.put("2차검수", fileStats.getOrDefault("2차검수", 0) + 1);

        // 서브키 통계를 추가 후, 그룹 통계에 갱신
        groupMap.put(subKey, fileStats);

        // 그룹 통계를 다시 전체 통계 맵에 업데이트
        statsMap.put(groupKey, groupMap);

        // 최종적으로 resultMap에 업데이트된 통계를 저장
        resultMap.put(key, statsMap);
    }

    // 질환별 통계 업데이트
    private void updateDiseaseStats(Map<String, Object> resultMap, String diseaseClass, String institutionId, int labelingStatus, int firstCheckStatus, int secondCheckStatus) {
        updateStats(resultMap, "질환별", diseaseClass, institutionId, labelingStatus, firstCheckStatus, secondCheckStatus);
    }

    // 대학별 통계 업데이트
    private void updateInstitutionStats(Map<String, Object> resultMap, String institutionId, String diseaseClass, int labelingStatus, int firstCheckStatus, int secondCheckStatus) {
        updateStats(resultMap, "대학별", institutionId, diseaseClass, labelingStatus, firstCheckStatus, secondCheckStatus);
    }

    // 파일 존재 여부 확인
    private boolean checkFileExistsInSubfolders(File folder, String fileName) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));
        if (files != null) {
            for (File file : files) {
                if (file.exists()) return true;
            }
        }
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                if (checkFileExistsInSubfolders(subFolder, fileName)) return true;
            }
        }
        return false;
    }

    private File getFileInSubfolders(File folder, String fileName) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().equals(fileName.toLowerCase()));
        if (files != null) {
            for (File file : files) {
                if (file.exists()) return file;
            }
        }
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                File foundFile = getFileInSubfolders(subFolder, fileName);
                if (foundFile != null) return foundFile;
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
                if (key.equals("Labeling_Info") && firstElement.get("Labelling").asText().equals(targetValue)) {
                    return 2;
                }
                if (key.equals("First_Check_Info") && firstElement.get("Checking1").asText().equals("2")) {
                    return 2;
                }
                if (key.equals("Second_Check_Info") && firstElement.get("Checking2").asText().equals("2")) {
                    return 2;
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

                Row headerRow = sheet.getRow(3); // 헤더가 4번째 행에 있다고 가정
                if (headerRow == null) {
                    continue;
                }

                // 헤더 인덱스 매핑
                Map<String, Integer> headerIndexMap = new HashMap<>();
                for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                    Cell cell = headerRow.getCell(cellIndex);
                    if (cell != null) {
                        String headerName = cell.getStringCellValue().trim();
                        headerIndexMap.put(headerName, cellIndex);
                    }
                }

                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        Map<String, String> rowData = new LinkedHashMap<>();

                        // IMAGE_ID 필드 처리
                        Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                        if (imageIdIndex != null) {
                            Cell imageIdCell = row.getCell(imageIdIndex);
                            String imageIdValue = (imageIdCell != null) ? imageIdCell.toString().trim() : "";
                            rowData.put("IMAGE_ID", imageIdValue);
                        }

                        // DISEASE_CLASS 필드 처리
                        Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                        if (diseaseClassIndex != null) {
                            Cell diseaseClassCell = row.getCell(diseaseClassIndex);
                            String diseaseClassValue = (diseaseClassCell != null) ? diseaseClassCell.toString().trim() : "";
                            rowData.put("DISEASE_CLASS", diseaseClassValue);
                        }

                        // INSTITUTION_ID 필드 처리
                        Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");
                        if (institutionIdIndex != null) {
                            Cell institutionIdCell = row.getCell(institutionIdIndex);
                            String institutionIdValue = (institutionIdCell != null) ? institutionIdCell.toString().trim() : "";
                            rowData.put("INSTITUTION_ID", institutionIdValue);
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
