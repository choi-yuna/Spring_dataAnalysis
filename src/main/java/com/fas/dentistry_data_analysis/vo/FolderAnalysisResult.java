package com.fas.dentistry_data_analysis.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
@Data
public class FolderAnalysisResult {
    private String folderPath;
    private LocalDate analysisDate;
    private Map<String, FileAnalysisResult> files = new HashMap<>();

    // Getters and Setters
}
