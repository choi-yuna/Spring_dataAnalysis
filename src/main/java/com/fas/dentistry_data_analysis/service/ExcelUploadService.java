package com.fas.dentistry_data_analysis.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class ExcelUploadService{

   private final FileStorageService fileStorageService;
    @Autowired
    public ExcelUploadService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // 파일을 서버에 저장하고 고유한 파일 ID를 반환하는 메소드
    public String storeFile(MultipartFile file) throws IOException {
        String fileId = fileStorageService.generateFileId();
        String tempDir = System.getProperty("java.io.tmpdir");
        // 파일명에 포함된 공백이나 특수 문자를 제거
        String sanitizedFileName = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("[^a-zA-Z0-9.\\-]", "_");
        Path filePath = Paths.get(tempDir, fileId + "_" + sanitizedFileName);

        try {
            // 파일 저장
            Files.write(filePath, file.getBytes());
            // 파일이 제대로 저장되었는지 확인하는 로그
            if (Files.exists(filePath)) {
                log.info("파일 저장 성공 : {}" , filePath.toString());
            } else {
                log.info("파일 저장 실패: {}",  filePath.toString());
            }

            // 파일 ID와 경로를 저장
            fileStorageService.storeFilePath(fileId, filePath);
        } catch (IOException e) {
            System.err.println("파일 저장 중 오류 발생: " + e.getMessage());
            throw e;
        }

        return fileId; // 고유한 파일 ID 반환
    }

}