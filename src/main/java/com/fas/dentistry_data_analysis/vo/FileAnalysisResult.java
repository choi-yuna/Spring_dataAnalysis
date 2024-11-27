package com.fas.dentistry_data_analysis.vo;

import lombok.Data;

@Data
public class FileAnalysisResult {
    private String fileName;
    private boolean processed;
    private String status;
    private int goalCount;
    private int labelingCount;
    private int firstCheck;
    private int secondCheck;
    private int buildRate;

    // Getters and Setters
}