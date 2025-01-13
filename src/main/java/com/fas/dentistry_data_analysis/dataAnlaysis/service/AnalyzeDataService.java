package com.fas.dentistry_data_analysis.dataAnlaysis.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface AnalyzeDataService {
    List<Map<String, Map<String, String>>> analyzeData(String[] fileIds, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException;
    List<Map<String, Map<String, String>>>  analyzeFolderData(String folderPath, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException;
   List<Map<String, Map<String, String>>> analyzeJsonData(String folderPath, String diseaseClass, int institutionId) throws IOException, ExecutionException, InterruptedException;

    List<Map<String, Object>> analyzeDataWithFilters(String[] fileIds, Map<String, String> filterConditions, List<String> headers) throws IOException;
    List<Map<String, Object>> analyzeFolderDataWithFilters(String folderPath, Map<String, String> filterConditions, List<String> headers) throws IOException;

    List<Map<String, Object>> analyzeJsonDataWithFilters(String s, Map<String, String> filters, List<String> headers);
}
