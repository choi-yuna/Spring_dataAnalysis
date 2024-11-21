package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class AnalyzeBoardServiceImpl {

    private final DataGropedService dataGropedService;
    private final ExcelService excelService;

    // SFTP 서버 정보
    private static final String SFTP_HOST = "202.86.11.27";  // SFTP 서버 IP
    private static final int SFTP_PORT = 22;  // SFTP 포트
    private static final String SFTP_USER = "dent_fas";  // 사용자 계정
    private static final String SFTP_PASSWORD = "dent_fas123";  // 비밀번호

    public AnalyzeBoardServiceImpl(DataGropedService dataGropedService, ExcelService excelService) {
        this.dataGropedService = dataGropedService;
        this.excelService = excelService;
    }

    // controller에서 실행되는 메서드
    public Map<String, Object> processFilesInFolder(String folderPath) throws Exception {
        // 결과를 저장할 리스트 (기관과 질환을 포함한 모든 항목이 리스트에 저장됩니다)
        List<Map<String, Object>> resultList = new ArrayList<>();

        // SFTP 연결하여 폴더 내 모든 .xlsx 파일 처리
        processFolderRecursively(folderPath, resultList);

        // 질환별 데이터와 기관별 데이터를 각각 처리
        Map<String, Object> response = new HashMap<>();

        // 질환별 데이터 처리
        List<Map<String, Object>> diseaseData = dataGropedService.groupDataByDisease(resultList);
        diseaseData.add(dataGropedService.createAllData(resultList, "INSTITUTION_ID", "질환 ALL"));  // 질환ALL 데이터 추가
        response.put("질환별", diseaseData);

        // 기관별 데이터 처리
        List<Map<String, Object>> institutionData = dataGropedService.groupDataByInstitution(resultList);
        institutionData.add(dataGropedService.createAllData(resultList, "DISEASE_CLASS", "기관 ALL"));  // 기관ALL 데이터 추가
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
                    List<Map<String, Object>> filteredData = excelService.processExcelFile(inputStream);
                    resultList.addAll(filteredData);
                    log.info("Processed {} rows from file: {}", filteredData.size(), fileName);

                    // 엑셀에서 가져온 데이터를 기반으로 institutionId와 diseaseClass를 추출하여 상태 업데이트
                    for (Map<String, Object> row : filteredData) {
                        String imageId = (String) row.get("IMAGE_ID");
                        String diseaseClass = (String) row.get("DISEASE_CLASS");
                        String institutionId = (String) row.get("INSTITUTION_ID");



                        // 이미지 ID를 기반으로 .dcm, .json, .ini 파일 존재 여부 체크
                        boolean dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm");
                        boolean jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                        boolean iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                        // .dcm, .json, .ini 파일이 모두 존재하지 않으면 '데이터구성검수' 증가
                        if (!(dcmExists && jsonExists && iniExists)) {
                            incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                        }

                        // jsonExists가 true일 때 JSON 파일 상태 체크
                        if (jsonExists) {
                            log.info("{}", folderPath);
                            // 예시: 각 호출마다 새로 InputStream을 읽어 처리
                            InputStream jsonFileStream = SFTPClient.readFile(channelSftp, folderPath + "/Labelling/meta", imageId + ".json");
                            int labelingStatus = getJsonStatus(jsonFileStream, "Labeling_Info", "완료");

                            jsonFileStream = SFTPClient.readFile(channelSftp, folderPath + "/Labelling/meta", imageId + ".json");
                            int firstCheckStatus = getJsonStatus(jsonFileStream, "First_Check_Info", "2");

                            jsonFileStream = SFTPClient.readFile(channelSftp, folderPath + "/Labelling/meta", imageId + ".json");
                            int secondCheckStatus = getJsonStatus(jsonFileStream, "Second_Check_Info", "2");

                            log.info("라벨링{} 1치검수{} 2차검수{}",labelingStatus,firstCheckStatus, secondCheckStatus);

                            if (labelingStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링건수");
                            if (firstCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "1차검수");
                            if (secondCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "2차검수");
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

    private void incrementStatus(List<Map<String, Object>> resultList, String institutionId, String diseaseClass, String imageId, String status) {
        // IMAGE_ID, INSTITUTION_ID, DISEASE_CLASS를 기준으로 항목을 찾습니다.
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> imageId.equals(item.get("IMAGE_ID")) && institutionId.equals(item.get("INSTITUTION_ID")) && diseaseClass.equals(item.get("DISEASE_CLASS")))
                .findFirst();

        // 해당 항목이 없다면 새로 추가합니다.
        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("IMAGE_ID", imageId);
            newEntry.put("INSTITUTION_ID", institutionId);
            newEntry.put("DISEASE_CLASS", diseaseClass);
            newEntry.put("목표건수", 0);  // 목표건수는 나중에 계산
            newEntry.put("라벨링건수", 0);
            newEntry.put("1차검수", 0);
            newEntry.put("데이터구성검수", 0);
            newEntry.put("2차검수", 0);
            newEntry.put("목표건수_증가", false);  // 목표건수 증가 여부를 초기값 false로 설정
            resultList.add(newEntry);
            existing = Optional.of(newEntry);
        }

        Map<String, Object> statusMap = existing.get();

        // 목표건수 증가 여부 체크 (Boolean 값을 null이 아닌 기본값인 false로 처리)
        boolean incrementGoalCount = false;

        // 상태 값이 "라벨링건수", "1차검수", "2차검수"일 때 한 번만 목표건수를 증가시키도록 처리
        if ("라벨링건수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }
        if ("1차검수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }
        if ("2차검수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }

        // 목표건수 증가 여부가 true일 경우에만 목표건수를 증가시킴
        if (incrementGoalCount) {
            Integer currentGoalCount = (Integer) statusMap.get("목표건수");
            if (currentGoalCount == null) {
                currentGoalCount = 0;
            }
            statusMap.put("목표건수", currentGoalCount + 1);  // 목표건수 증가
            statusMap.put("목표건수_증가", true);  // 목표건수 증가 여부 표시
        }

        // 각 상태별 카운트 (라벨링건수, 1차검수, 2차검수) 증가
        Integer currentStatusValue = (Integer) statusMap.get(status);
        if (currentStatusValue == null) {
            currentStatusValue = 0;
        }
        statusMap.put(status, currentStatusValue + 1);
    }


    // SFTP에서 파일 존재 여부 확인
    private boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName) throws SftpException {
        // 현재 폴더 내 파일 검색
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);

        // 파일 존재 여부 확인
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getFilename().equals(fileName)) {
                return true;
            }
        }

        // 하위 폴더를 재귀적으로 탐색
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                // 하위 폴더에서 파일을 찾도록 재귀 호출
                boolean foundInSubfolder = checkFileExistsInSFTP(channelSftp, folderPath + "/" + entry.getFilename(), fileName);
                if (foundInSubfolder) {
                    return true;
                }
            }
        }

        return false;
    }

    // SFTP에서 파일 존재 여부 확인 (하위 폴더 경로 포함)
    private boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName, String subFolder) throws SftpException {
        // 하위 폴더 경로를 포함한 전체 경로로 파일을 찾음
        String targetPath = folderPath + subFolder;

        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, targetPath);

        // 파일 존재 여부 확인
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
        log.debug("infoNode: {}", infoNode); // infoNode 자체가 null인지 확인


        if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
            JsonNode firstElement = infoNode.get(0);

            log.debug("Current key: " + key);

            if (key.equals("Labeling_Info")) {
                JsonNode labelingStatusNode = firstElement.get("Labelling");
                if (labelingStatusNode != null && labelingStatusNode.asText().equals(targetValue)) {
                    return 2;
                }
            }

            if (key.equals("First_Check_Info")) {
                JsonNode checkResultNode = firstElement.get("Checking1");
                if (checkResultNode != null) {
                    log.debug("First_Check_Info - Checking1: " + checkResultNode.asText());
                    if (checkResultNode.asText().equals(targetValue)) {
                        return 2;
                    }
                } else {
                    log.debug("First_Check_Info - Checking1 is null or missing");
                }
            }

            if (key.equals("Second_Check_Info")) {
                JsonNode checkResultNode = firstElement.get("Checking2");
                if (checkResultNode != null) {
                    log.debug("Second_Check_Info - Checking2: " + checkResultNode.asText());
                    if (checkResultNode.asText().equals(targetValue)) {
                        return 2;
                    }
                } else {
                    log.debug("Second_Check_Info - Checking2 is null or missing");
                }
            }
        }
        return 0;
    }
}
