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
import java.time.LocalDate;
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

        // SFTP 연결을 한 번 열고 모든 처리를 마친 뒤 닫습니다.
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = SFTPClient.createSession(SFTP_HOST, SFTP_USER, SFTP_PASSWORD, SFTP_PORT);
            channelSftp = SFTPClient.createSftpChannel(session);

            // SFTP 연결된 상태에서 폴더 내 파일 처리
            processFolderRecursively(channelSftp, folderPath, resultList);
        } finally {
            // SFTP 연결 종료
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }

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

        // 대시보드 데이터를 추가
        response.put("대시보드", getDashboardData(resultList));

        return response;
    }

    private void processFolderRecursively(ChannelSftp channelSftp, String folderPath, List<Map<String, Object>> resultList) throws Exception {
        log.info("Processing folder: {}", folderPath);

        // 폴더 내 모든 .xlsx 파일 목록을 가져옵니다.
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);
        log.info("Found {} files in folder: {}", files.size(), folderPath);

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

                // 상태 업데이트
                for (Map<String, Object> row : filteredData) {
                    String imageId = (String) row.get("IMAGE_ID");
                    String diseaseClass = (String) row.get("DISEASE_CLASS");
                    String institutionId = (String) row.get("INSTITUTION_ID");

                    boolean dcmExists = false;
                    boolean iniExists = false;
                    boolean jsonExists = false;

                    if (folderPath.contains("구강암")) {
                        log.info("Processed {} {}", imageId, folderPath);
                        // 구강암 폴더에 맞는 파일 체크
                        dcmExists = checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);
                        jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                        iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");

                        // 구강암 폴더일 경우 특정 파일들이 모두 존재하지 않으면 '데이터구성검수' 증가
                        if (!(dcmExists && jsonExists && iniExists)) {
                            incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                        }

                        // jsonExists가 true일 때 JSON 파일 상태 체크
                        if (jsonExists) {
                            processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                        }
                    } else {
                        // 치주질환 폴더에 맞는 파일 체크
                        dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm","");
                        jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                        iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");

                        // .dcm, .json, .ini 파일이 모두 존재하지 않으면 '데이터구성검수' 증가
                        if (!(dcmExists && jsonExists && iniExists)) {
                            incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                        }

                        // jsonExists가 true일 때 JSON 파일 상태 체크
                        if (jsonExists) {
                            processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                        }
                    }
                }
            }
        }

        // 하위 폴더 재귀 호출
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                processFolderRecursively(channelSftp, folderPath + "/" + entry.getFilename(), resultList);
            }
        }
    }
    private boolean checkFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) throws SftpException {
        // 폴더 내 모든 파일과 디렉터리 목록을 가져옵니다.
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);

        // 해당 폴더 내에서 imageId를 포함한 폴더를 찾습니다
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && entry.getFilename().contains(imageId)) {  // imageId가 포함된 폴더 찾기
                String targetFolderPath = folderPath + "/" + entry.getFilename();  // 해당 폴더 경로

                // 해당 폴더 내에서 .dcm 확장자를 가진 파일이 하나라도 있는지 확인
                Vector<ChannelSftp.LsEntry> subFiles = SFTPClient.listFiles(channelSftp, targetFolderPath);
                for (ChannelSftp.LsEntry subEntry : subFiles) {
                    if (subEntry.getFilename().endsWith(".dcm")) {
                        return true;  // .dcm 파일이 하나라도 있으면 true 반환
                    }
                }
            }
        }

        return false; // 해당 폴더 내에 .dcm 파일이 없으면 false 반환
    }


    private void processJsonFile(ChannelSftp channelSftp, String folderPath, String imageId, List<Map<String, Object>> resultList, String institutionId, String diseaseClass) throws Exception {
        String jsonFilePath = folderPath + (folderPath.contains("구강암") ? "/Labelling/" : "/Labelling/meta/");
        String labelingKey = (folderPath.contains("구강암") ? "Labeling_info" : "Labeling_Info");
        String firstCheckKey = (folderPath.contains("구강암") ? "First_Check_info" : "First_Check_Info");
        String secondCheckKey = (folderPath.contains("구강암") ? "Second_Check_info" : "Second_Check_Info");

        // JSON 파일을 한 번만 읽음
        InputStream jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json");
        int labelingStatus = getJsonStatus(jsonFileStream, labelingKey);

        jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json");
        int firstCheckStatus = getJsonStatus(jsonFileStream, firstCheckKey);

        jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json");
        int secondCheckStatus = getJsonStatus(jsonFileStream,secondCheckKey );

        log.info("라벨링{} 1차검수{} 2차검수{}", labelingStatus, firstCheckStatus, secondCheckStatus);

        if (labelingStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링건수");
        if (firstCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "1차검수");
        if (secondCheckStatus == 2) incrementStatus(resultList, institutionId, diseaseClass, imageId, "2차검수");
    }

    private Map<String, Object> getDashboardData(List<Map<String, Object>> resultList) {
        int totalFilesCount = resultList.size();
        long errorFilesCount = resultList.stream()
                .filter(row -> "데이터구성검수".equals(row.get("status")))
                .count();
        String uploadDate = LocalDate.now().toString();

        List<Map<String, Object>> statuses = new ArrayList<>();
        Map<String, Object> totalFilesStatus = new HashMap<>();
        totalFilesStatus.put("type", "총파일 수");
        totalFilesStatus.put("fileCount", totalFilesCount);
        totalFilesStatus.put("uploadDate", uploadDate);
        totalFilesStatus.put("totalFilesCount", totalFilesCount);
        statuses.add(totalFilesStatus);

        Map<String, Object> errorFilesStatus = new HashMap<>();
        errorFilesStatus.put("type", "오류 파일 수");
        errorFilesStatus.put("fileCount", errorFilesCount);
        errorFilesStatus.put("uploadDate", uploadDate);
        errorFilesStatus.put("totalFilesCount", totalFilesCount);
        statuses.add(errorFilesStatus);

        Map<String, Object> builtRateStatus = new HashMap<>();
        builtRateStatus.put("type", "구축율");
        builtRateStatus.put("fileCount", totalFilesCount - errorFilesCount);
        builtRateStatus.put("uploadDate", uploadDate);
        builtRateStatus.put("totalFilesCount", totalFilesCount);
        statuses.add(builtRateStatus);

        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("statuses", statuses);
        return dashboardData;
    }

    // 중복된 IMAGE_ID를 처리하는 방식 개선
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

    private int getJsonStatus(InputStream jsonFileStream, String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonFileStream);

        // Key에 대한 여러 가지 이름 변형을 처리
        String[] possibleKeys = getPossibleKeysForKey(key);

        // 각 key 이름 변형에 대해 처리
        for (String possibleKey : possibleKeys) {
            JsonNode infoNode = rootNode.get(possibleKey);

            if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
                JsonNode firstElement = infoNode.get(0);

                // Labeling 상태 확인
                if (possibleKey.equals("Labeling_Info") || possibleKey.equals("Labeling_info")) {
                    JsonNode labelingStatusNode = getField(firstElement, "Labelling", "Labeling");
                    if (labelingStatusNode != null &&
                            (labelingStatusNode.asText().equals("2") || labelingStatusNode.asText().equals("완료"))) {
                        return 2;
                    }
                }

                // First_Check 상태 확인
                if (possibleKey.equals("First_Check_Info") || possibleKey.equals("First_Check_info")) {
                    JsonNode checkResultNode = getField(firstElement, "Checking1", "Checking_1");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;
                    }
                }

                // Second_Check 상태 확인
                if (possibleKey.equals("Second_Check_Info") || possibleKey.equals("Second_Check_info")) {
                    JsonNode checkResultNode = getField(firstElement, "Checking2", "Checking_2");
                    if (checkResultNode != null && checkResultNode.asText().equals("2")) {
                        return 2;
                    }
                }
            }
        }

        return 0;
    }

    // key에 따라 두 가지 필드를 반환하는 메서드
    private JsonNode getField(JsonNode node, String key1, String key2) {
        // 첫 번째 키부터 시도하고, 없으면 두 번째 키를 시도
        JsonNode fieldNode = node.get(key1);
        if (fieldNode == null) {
            fieldNode = node.get(key2);
        }
        return fieldNode;
    }

    private String[] getPossibleKeysForKey(String key) {
        // 각 key에 대해 가능한 이름 변형을 반환
        switch (key) {
            case "Labeling_Info":
                return new String[]{"Labeling_Info", "Labeling_info"};
            case "First_Check_Info":
                return new String[]{"First_Check_Info", "First_Check_info"};
            case "Second_Check_Info":
                return new String[]{"Second_Check_Info", "Second_Check_info"};
            default:
                return new String[]{key};  // 기본적으로 동일한 이름 사용
        }
    }


}
