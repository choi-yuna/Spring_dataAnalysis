package com.fas.dentistry_data_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

//파일의 분석 결과를 저장하는 엔터티 클래스

@Entity
@Table(name = "analysis_results")
@Getter
@Setter
public class AnalysisResults {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String analysisResult;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
