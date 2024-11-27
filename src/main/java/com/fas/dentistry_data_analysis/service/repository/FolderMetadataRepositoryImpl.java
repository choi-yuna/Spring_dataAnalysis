package com.fas.dentistry_data_analysis.service.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class FolderMetadataRepositoryImpl implements FolderMetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    public FolderMetadataRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Boolean> isFolderAnalyzed(String folderPath) {
        String sql = "SELECT is_analyzed FROM folder_metadata WHERE folder_path = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Boolean.class, folderPath));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void saveFolder(String folderPath, boolean isAnalyzed, LocalDateTime analyzedAt) {
        String sql = "INSERT INTO folder_metadata (folder_path, is_analyzed, analyzed_at) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, folderPath, isAnalyzed, analyzedAt);
    }

    @Override
    public void updateAnalyzedStatus(String folderPath, boolean isAnalyzed, LocalDateTime analyzedAt) {
        String sql = "UPDATE folder_metadata SET is_analyzed = ?, analyzed_at = ? WHERE folder_path = ?";
        jdbcTemplate.update(sql, isAnalyzed, analyzedAt, folderPath);
    }
}
