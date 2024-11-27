package com.fas.dentistry_data_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_analysis_status")
@Getter
@Setter
//SFTP 파일의 상태 정보를 관리하기 위한 엔터티 클래스

public class FileAnalysisStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fileId;

    @Column(nullable = false)
    private String folderPath;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    @Column(nullable = false)
    private String analysisStatus;

    private LocalDateTime lastAnalyzed;

    @Column(columnDefinition = "JSON")
    private String additionalInfo;
}
