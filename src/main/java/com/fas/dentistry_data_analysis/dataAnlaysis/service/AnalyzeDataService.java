package com.fas.dentistry_data_analysis.dataAnlaysis.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface AnalyzeDataService {
    List<Map<String, Map<String, String>>> analyzeData(List<String> filePath, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException;

    List<Map<String, Object>> analyzeDataWithFilters(List<String> filePath, Map<String, String> filterConditions, List<String> headers) throws IOException;
}

