package com.fas.dentistry_data_analysis.repository;

import com.fas.dentistry_data_analysis.entity.FileAnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileAnalysisStatusRepository extends JpaRepository<FileAnalysisStatus, Long> {
    Optional<FileAnalysisStatus> findByFileId(String fileId);
}