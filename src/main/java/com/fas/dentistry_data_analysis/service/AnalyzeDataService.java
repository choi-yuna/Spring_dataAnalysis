package com.fas.dentistry_data_analysis.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface AnalyzeDataService {
    List<Map<String, String>> analyzeData(String[] fileIds, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException;
    List<Map<String, String>> analyzeFolderData(String folderPath, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException;

    List<Map<String, Object>> analyzeDataWithFilters(String[] fileIds, Map<String, String> filterConditions, List<String> headers) throws IOException;
}
