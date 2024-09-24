package com.fas.dentistry_data_analysis.service;

import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {
    private final Map<String, Path> fileStorage = new ConcurrentHashMap<>();

    // 파일 ID와 경로를 저장하는 메소드
    public void storeFilePath(String fileId, Path filePath) {
        fileStorage.put(fileId, filePath);
    }

    // 파일 ID를 기반으로 경로를 조회하는 메소드
    public Path getFilePath(String fileId) {
        return fileStorage.get(fileId);
    }

    // 파일 ID 생성 메소드
    public String generateFileId() {
        return UUID.randomUUID().toString();
    }
}
