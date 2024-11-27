package com.fas.dentistry_data_analysis.service.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FolderMetadataRepository {
    Optional<Boolean> isFolderAnalyzed(String folderPath);
    void saveFolder(String folderPath, boolean isAnalyzed, LocalDateTime analyzedAt);
    void updateAnalyzedStatus(String folderPath, boolean isAnalyzed, LocalDateTime analyzedAt);
}
