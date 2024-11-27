package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.service.repository.FolderMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FolderMetadataService {

    private final FolderMetadataRepository folderMetadataRepository;

    @Autowired
    public FolderMetadataService(FolderMetadataRepository folderMetadataRepository) {
        this.folderMetadataRepository = folderMetadataRepository;
    }

    public void saveAnalyzedFolder(String folderPath) {
        Optional<Boolean> isAnalyzed = folderMetadataRepository.isFolderAnalyzed(folderPath);
        if (isAnalyzed.isEmpty()) {
            // 폴더 정보가 없으면 저장
            folderMetadataRepository.saveFolder(folderPath, true, LocalDateTime.now());
        } else if (!isAnalyzed.get()) {
            // 폴더 정보가 있으나 분석되지 않은 경우 업데이트
            folderMetadataRepository.updateAnalyzedStatus(folderPath, true, LocalDateTime.now());
        }
    }

    public boolean isFolderAlreadyAnalyzed(String folderPath) {
        return folderMetadataRepository.isFolderAnalyzed(folderPath).orElse(false);
    }
}
