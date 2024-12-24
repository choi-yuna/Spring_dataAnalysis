package com.fas.dentistry_data_analysis.service.Json;


import com.fas.dentistry_data_analysis.config.StorageConfig;
import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
@Slf4j
public class JSONService {

    private final StorageConfig storageConfig;

    public JSONService(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    public void saveExcelToLocal(ChannelSftp channelSftp, String folderPath, String fileName, String diseaseClass, String institutionId, String storagePath) {
        try {
            // 저장할 디렉터리 설정
            File dentistryDir = new File("C:/app/dentistry");
            if (!dentistryDir.exists()) {
                dentistryDir.mkdirs(); // 디렉터리가 없으면 생성
            }
            String uuid = UUID.randomUUID().toString();
            // 파일 이름에 질환과 기관 정보를 추가
            String newFileName = String.format("%s_%s_%s_%s", diseaseClass, institutionId, uuid,".xlsx");
            File localFile = new File(dentistryDir, newFileName);

            // 파일이 이미 존재하면 다시 다운로드하지 않음
            if (!localFile.exists()) {
                try (InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);
                     OutputStream outputStream = new FileOutputStream(localFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                log.info("Excel file saved to: {}", localFile.getAbsolutePath());
            } else {
                log.info("Excel file already exists at: {}", localFile.getAbsolutePath());
            }
        } catch (IOException | SftpException e) {
            log.error("Error saving Excel file locally: {}", fileName, e);
        }
    }

    public void savePassIdsToJson(List<String> passIds, String savePath) {
        try {
            File dentistryDir = new File(savePath);
            if (!dentistryDir.exists()) {
                dentistryDir.mkdirs(); // 디렉터리가 없으면 생성
            }
            // JSON 형식으로 변환
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonContent = objectMapper.writeValueAsString(passIds);

            String newFileName = String.format("%s","pass_ids.json");
            File localFile = new File(dentistryDir, newFileName);

            try (FileWriter fileWriter = new FileWriter(localFile)) {
                fileWriter.write(jsonContent);
            }

            log.info("Pass된 ID가 JSON 파일로 저장되었습니다: {}", savePath);
        } catch (IOException e) {
            log.error("Pass된 ID를 JSON 파일로 저장하는 중 오류가 발생했습니다.", e);
        }
    }

    public List<Map<String, Object>> loadResultsFromJsonSftp(String folderPath, ChannelSftp channelSftp) throws IOException, SftpException {
        // JSON 파일 로드
        try (InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, "analysis_result.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
        }
    }

    public void saveResultsToJsonSftp(String folderPath, List<Map<String, Object>> newResults, ChannelSftp channelSftp) throws IOException, SftpException {
        // 기존 JSON 데이터 로드
        List<Map<String, Object>> existingResults;
        try {
            existingResults = loadResultsFromJsonSftp(folderPath, channelSftp);
        } catch (Exception e) {
            log.warn("No existing JSON file found or error loading JSON from folder: {}. Initializing with empty list.", folderPath);
            existingResults = new ArrayList<>();
        }

        // 새로운 결과 병합
        List<Map<String, Object>> mergedResults = new ArrayList<>(existingResults);
        mergedResults.addAll(newResults);

        // ObjectMapper를 사용하여 JSON 형식으로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonContent = objectMapper.writeValueAsString(mergedResults);

        // 문자열 데이터를 InputStream으로 변환
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());

        // SFTP 서버에 저장 (폴더 경로 + 파일 이름 지정)
        String sftpFilePath = folderPath + "/analysis_result.json";

        SFTPClient.uploadFile(channelSftp, folderPath, "analysis_result.json", inputStream);

        log.info("Results successfully merged and saved to SFTP at: {}", sftpFilePath);
    }


    public void deleteExistingExcelFiles(String path,String filename) {
        // 저장 디렉토리
        String storagePath = storageConfig.getStoragePath();
        File dentistryDir = new File(path);

        if (!dentistryDir.exists()) {
            log.info("Directory does not exist: {}", dentistryDir.getAbsolutePath());
            return;
        }

        // 질환 및 기관에 해당하는 파일만 삭제
        File[] filesToDelete = dentistryDir.listFiles((dir, name) ->
                name.endsWith(filename)
        );

        if (filesToDelete != null) {
            for (File file : filesToDelete) {
                if (file.delete()) {
                    log.info("Deleted existing file: {}", file.getName());
                } else {
                    log.warn("Failed to delete file: {}", file.getName());
                }
            }
        }
    }
    public Set<String> loadPassIdsFromJson(String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // JSON 파일에서 단순 리스트로 데이터 읽기
            List<String> idList = objectMapper.readValue(new File(filePath), new TypeReference<List<String>>() {});
            return new HashSet<>(idList); // Set으로 변환하여 반환
        } catch (IOException e) {
            log.error("Pass된 ID를 JSON에서 읽는 중 오류가 발생했습니다: {}", filePath, e);
            return Collections.emptySet(); // 실패 시 빈 Set 반환
        }
    }



    public void saveJsonToLocal(String savePath, String fileName, JsonNode newJsonData) {
        try {
            // 저장 디렉토리 생성
            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdirs(); // 디렉터리가 없으면 생성
            }

            // 파일 경로 생성
            File jsonFile = new File(saveDir, fileName);
            ObjectMapper objectMapper = new ObjectMapper();

            // 기존 데이터 로드
            List<JsonNode> existingData = new ArrayList<>();
            if (jsonFile.exists()) {
                try (FileInputStream fis = new FileInputStream(jsonFile)) {
                    existingData = objectMapper.readValue(fis, new TypeReference<List<JsonNode>>() {});
                } catch (Exception e) {
                    log.warn("기존 JSON 파일을 로드하는 중 오류가 발생했습니다. 새로 생성합니다: {}", jsonFile.getAbsolutePath(), e);
                }
            }

            // 새로운 데이터 추가
            existingData.add(newJsonData);

            // 병합된 데이터를 다시 파일에 저장
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, existingData);
        } catch (IOException e) {
            log.error("JSON 데이터를 로컬에 저장하는 중 오류가 발생했습니다.", e);
        }
    }


}
