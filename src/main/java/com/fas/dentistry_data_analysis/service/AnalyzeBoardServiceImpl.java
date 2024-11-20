package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class AnalyzeBoardServiceImpl {

    // SFTP 서버 정보
    private static final String SFTP_HOST = "202.86.11.27";  // SFTP 서버 IP
    private static final int SFTP_PORT = 22;  // SFTP 포트
    private static final String SFTP_USER = "dent_fas";  // 사용자 계정
    private static final String SFTP_PASSWORD = "dent_fas123";  // 비밀번호

    public Map<String, Object> processFilesInFolder(String folderPath) throws Exception {
        // 결과를 저장할 리스트 (기관과 질환을 포함한 모든 항목이 리스트에 저장됩니다)
        List<Map<String, Object>> resultList = new ArrayList<>();

        // SFTP 연결하여 폴더 내 모든 .xlsx 파일 처리
        processFolderRecursively(folderPath, resultList);
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

    private void processFolderRecursively(String folderPath, List<Map<String, Object>> resultList) throws Exception {
        log.info("Processing folder: {}", folderPath);

        ChannelSftp channelSftp = null;
        Session session = null;

        try {
            // SFTP 연결
            session = SFTPClient.createSession(SFTP_HOST, SFTP_USER, SFTP_PASSWORD, SFTP_PORT);
            channelSftp = SFTPClient.createSftpChannel(session);

            // 폴더 내 모든 .xlsx 파일 목록을 가져옵니다.
            Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);
            log.info("Found {} files in folder", files.size());

            // .xlsx 파일만 처리
            for (ChannelSftp.LsEntry entry : files) {
                String fileName = entry.getFilename();
                if (fileName.endsWith(".xlsx")) {
                    log.info("Processing file: {}", fileName);
                    InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);

                    // 엑셀 파일 처리
                    List<Map<String, Object>> filteredData = processExcelFile(inputStream);
                    resultList.addAll(filteredData);
                    log.info("Processed {} rows from file: {}", filteredData.size(), fileName);

                    // 엑셀에서 가져온 데이터를 기반으로 institutionId와 diseaseClass를 추출하여 상태 업데이트
                    for (Map<String, Object> row : filteredData) {
                        String institutionId = (String) row.get("INSTITUTION_ID");
                        String diseaseClass = (String) row.get("DISEASE_CLASS");

                        // .dcm, .json, .ini 파일 존재 여부 체크
                        boolean dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, institutionId + ".dcm");
                        boolean jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, institutionId + ".json");
                        boolean iniExists = checkFileExistsInSFTP(channelSftp, folderPath, institutionId + ".ini");

                        // .dcm, .json, .ini 파일이 모두 존재하지 않으면 '데이터구성검수' 증가
                        if (!(dcmExists && jsonExists && iniExists)) {
                            incrementStatus(resultList, institutionId, diseaseClass, "데이터구성검수");
                        }

                        // jsonExists가 true일 때 JSON 파일 상태 체크
                        if (jsonExists) {
                            InputStream jsonFileStream = SFTPClient.readFile(channelSftp, folderPath, institutionId + ".json");
                            int labelingStatus = getJsonStatus(jsonFileStream, "Labeling_Info", "완료");
                            int firstCheckStatus = getJsonStatus(jsonFileStream, "First_Check_Info", "2");
                            int secondCheckStatus = getJsonStatus(jsonFileStream, "Second_Check_Info", "2");

                            if (labelingStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "라벨링건수");
                            if (firstCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "1차검수");
                            if (secondCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, "2차검수");
                        }
                    }
                }
            }

            // 하위 폴더 재귀 호출
            for (ChannelSftp.LsEntry entry : files) {
                if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    processFolderRecursively(folderPath + "/" + entry.getFilename(), resultList);
                }
            }

        } catch (JSchException | SftpException e) {
            log.error("Error processing SFTP files", e);
            throw new Exception("SFTP 연결 또는 파일 처리 오류");
        } finally {
            // SFTP 연결 종료
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }
    }



    private void incrementStatus(List<Map<String, Object>> resultList, String institutionId, String diseaseClass, String status) {
        // 해당 기관과 질환에 맞는 항목을 찾습니다.
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> institutionId.equals(item.get("INSTITUTION_ID")) && diseaseClass.equals(item.get("DISEASE_CLASS")))
                .findFirst();

        // 해당 항목이 없다면 새로 추가합니다.
        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("INSTITUTION_ID", institutionId);
            newEntry.put("DISEASE_CLASS", diseaseClass);
            newEntry.put("목표건수", 0);
            newEntry.put("라벨링건수", 0);
            newEntry.put("1차검수", 0);
            newEntry.put("데이터구성검수", 0);
            newEntry.put("2차검수", 0);
            resultList.add(newEntry);
            existing = Optional.of(newEntry);
        }

        // 해당 항목을 찾아 상태값을 증가시킵니다.
        Map<String, Object> statusMap = existing.get();

        // 기존 값이 null일 경우 0으로 초기화 후 증가
        Integer currentStatusValue = (Integer) statusMap.get(status);
        if (currentStatusValue == null) {
            currentStatusValue = 0;  // 기본값 0 설정
        }
        statusMap.put(status, currentStatusValue + 1);
    }


    // SFTP에서 파일 존재 여부 확인
    private boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName) throws SftpException {
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getFilename().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    // JSON 파일에서 상태를 추출하는 메소드
    private int getJsonStatus(InputStream jsonFileStream, String key, String targetValue) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonFileStream);
        JsonNode infoNode = rootNode.get(key);

        if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
            JsonNode firstElement = infoNode.get(0);
            if (key.equals("Labeling_Info")) {
                JsonNode labelingStatusNode = firstElement.get("Labelling");
                if (labelingStatusNode != null && labelingStatusNode.asText().equals(targetValue)) {
                    return 2;
                }
            }

            if (key.equals("First_Check_Info")) {
                JsonNode checkResultNode = firstElement.get("Checking1");
                if (checkResultNode != null && checkResultNode.asText().equals(targetValue)) {
                    return 2;
                }
            }

            if (key.equals("Second_Check_Info")) {
                JsonNode checkResultNode = firstElement.get("Checking2");
                if (checkResultNode != null && checkResultNode.asText().equals(targetValue)) {
                    return 2;
                }
            }
        }
        return 0;
    }



    // 엑셀 파일 처리
    private List<Map<String, Object>> processExcelFile(InputStream inputStream) throws IOException {
        log.info("Processing Excel file");

        List<Map<String, Object>> filteredData = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            log.info("Excel file contains {} sheets", numberOfSheets);

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
                    continue;
                }

                Row headerRow = sheet.getRow(3); // Header row is at row index 3
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

                // 데이터를 읽어옵니다.
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        Map<String, Object> rowData = new LinkedHashMap<>();

                        Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                        Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                        Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                        // IMAGE_ID 추출
                        if (imageIdIndex != null) {
                            Cell imageIdCell = row.getCell(imageIdIndex);
                            String imageIdValue = (imageIdCell != null) ? ExcelUtils.getCellValueAsString(imageIdCell) : "";
                            rowData.put("IMAGE_ID", imageIdValue);
                        }

                        // DISEASE_CLASS 추출
                        if (diseaseClassIndex != null) {
                            Cell diseaseClassCell = row.getCell(diseaseClassIndex);
                            String diseaseClassValue = (diseaseClassCell != null) ? ExcelUtils.getCellValueAsString(diseaseClassCell) : "";
                            rowData.put("DISEASE_CLASS", diseaseClassValue);
                        }

                        // INSTITUTION_ID 추출
                        if (institutionIdIndex != null) {
                            Cell institutionIdCell = row.getCell(institutionIdIndex);
                            String institutionIdValue = (institutionIdCell != null) ? ExcelUtils.getCellValueAsString(institutionIdCell) : "";
                            rowData.put("INSTITUTION_ID", institutionIdValue);
                        }

                        if (!rowData.isEmpty()) {
                            filteredData.add(rowData);
                        }
                    }
                }
            }
        }
        log.info("Processed {} rows from Excel file", filteredData.size());
        return filteredData;
    }

    // 질환별로 데이터를 그룹화하는 메소드
    private List<Map<String, Object>> groupDataByDisease(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by disease. Number of entries: {}", resultList.size());
        log.info("{}",resultList.get(0));

        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String diseaseClass = (String) item.get("DISEASE_CLASS");
            String institutionId = (String) item.get("INSTITUTION_ID");

            if (!groupedData.containsKey(diseaseClass)) {
                groupedData.put(diseaseClass, new HashMap<>());
            }

            Map<String, Object> diseaseData = groupedData.get(diseaseClass);
            if (!diseaseData.containsKey("title")) {
                diseaseData.put("title", diseaseClass);
                diseaseData.put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                diseaseData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) diseaseData.get("totalData");

            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
            }

            // subData에 각 기관 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(institutionId);
            subRow.add(String.valueOf(goalCount));
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));

            // 43% 대신 실제 구축율 값 추가
            int buildRate = (int) totalData.get(5);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) diseaseData.get("subData");
            subData.add(subRow);
        }

        log.info("Finished grouping by disease. Number of grouped diseases: {}", groupedData.size());
        return formatGroupedData(groupedData);
    }

    private List<Map<String, Object>> groupDataByInstitution(List<Map<String, Object>> resultList) {
        log.info("Starting to group data by institution. Number of entries: {}", resultList.size());

        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> item : resultList) {
            String institutionId = (String) item.get("기관");
            String diseaseClass = (String) item.get("질환");

            if (!groupedData.containsKey(institutionId)) {
                groupedData.put(institutionId, new HashMap<>());
            }

            Map<String, Object> institutionData = groupedData.get(institutionId);
            if (!institutionData.containsKey("title")) {
                institutionData.put("title", institutionId);
                institutionData.put("totalData", new ArrayList<>(Collections.nCopies(6, 0))); // 초기값 설정
                institutionData.put("subData", new ArrayList<>());
            }

            // 총합 계산 (목표건수, 1차검수, 2차검수, 라벨링건수 등)
            List<Integer> totalData = (List<Integer>) institutionData.get("totalData");

            // "목표건수"가 null일 경우 0으로 설정
            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            totalData.set(0, totalData.get(0) + goalCount);

            // "라벨링건수"가 null일 경우 0으로 설정
            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            totalData.set(1, totalData.get(1) + labelingCount);

            // "1차검수"가 null일 경우 0으로 설정
            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            totalData.set(2, totalData.get(2) + firstCheck);

            // "데이터구성검수"가 null일 경우 0으로 설정
            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            totalData.set(3, totalData.get(3) + dataCheck);

            // "2차검수"가 null일 경우 0으로 설정
            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            totalData.set(4, totalData.get(4) + secondCheck);

            // 구축율 계산: (2차검수 / 목표건수) * 100
            if (goalCount > 0) {
                double buildRate = (double) secondCheck / goalCount * 100;
                totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
            }

            // subData에 각 질환 정보 추가
            List<String> subRow = new ArrayList<>();
            subRow.add(diseaseClass);
            subRow.add(String.valueOf(goalCount));
            subRow.add(String.valueOf(labelingCount));
            subRow.add(String.valueOf(firstCheck));
            subRow.add(String.valueOf(dataCheck));
            subRow.add(String.valueOf(secondCheck));

            // 실제 구축율 값 추가
            int buildRate = (int) totalData.get(5);
            subRow.add(buildRate + ""); // 구축율을 추가 (백분율)

            List<List<String>> subData = (List<List<String>>) institutionData.get("subData");
            subData.add(subRow);
        }

        log.info("Finished grouping by institution. Number of grouped institutions: {}", groupedData.size());
        return formatGroupedData(groupedData);
    }

    // 질환 ALL 또는 기관 ALL 데이터를 생성하는 통합 메소드
    private Map<String, Object> createAllData(List<Map<String, Object>> resultList, String groupingKey, String title) {
        log.info("Creating 'ALL' data for grouping key: {}", groupingKey);

        Map<String, Object> allData = new HashMap<>();
        allData.put("title", title);

        List<Integer> totalData = new ArrayList<>(Collections.nCopies(6, 0));
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

            // null 체크 및 기본값 설정
            int goalCount = (item.get("목표건수") != null) ? (int) item.get("목표건수") : 0;
            groupData.put("목표건수", (int) groupData.get("목표건수") + goalCount);

            int labelingCount = (item.get("라벨링건수") != null) ? (int) item.get("라벨링건수") : 0;
            groupData.put("라벨링건수", (int) groupData.get("라벨링건수") + labelingCount);

            int firstCheck = (item.get("1차검수") != null) ? (int) item.get("1차검수") : 0;
            groupData.put("1차검수", (int) groupData.get("1차검수") + firstCheck);

            int dataCheck = (item.get("데이터구성검수") != null) ? (int) item.get("데이터구성검수") : 0;
            groupData.put("데이터구성검수", (int) groupData.get("데이터구성검수") + dataCheck);

            int secondCheck = (item.get("2차검수") != null) ? (int) item.get("2차검수") : 0;
            groupData.put("2차검수", (int) groupData.get("2차검수") + secondCheck);

            // 총합 데이터 누적
            totalData.set(0, totalData.get(0) + goalCount);
            totalData.set(1, totalData.get(1) + labelingCount);
            totalData.set(2, totalData.get(2) + firstCheck);
            totalData.set(3, totalData.get(3) + dataCheck);
            totalData.set(4, totalData.get(4) + secondCheck);
        }

        // 구축율 계산: (2차검수 / 목표건수) * 100
        int secondCheck = totalData.get(4);
        int goalCount = totalData.get(0);

        if (goalCount > 0) {
            double buildRate = (double) secondCheck / goalCount * 100;
            totalData.set(5, (int) buildRate); // 구축율을 totalData의 6번째 항목에 넣기
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

            int buildRateForGroup = 0;
            if ((int) groupData.get("목표건수") > 0) {
                buildRateForGroup = (int) groupData.get("2차검수") * 100 / (int) groupData.get("목표건수");
            }
            subRow.add(buildRateForGroup + ""); // 구축율을 추가 (백분율)

            subData.add(subRow);
        }

        allData.put("subData", subData);

        log.info("Finished creating 'ALL' data for grouping key: {}", groupingKey);
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
}
