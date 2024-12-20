package com.fas.dentistry_data_analysis.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FileProcessor {
    public List<Map<String, Map<String, String>>> processFile(File file, String diseaseClass, int institutionId) throws IOException;
    public List<Map<String, Map<String, String>>> processServerFile(File file, String diseaseClass, int institutionId, Set<String> passIdsSet, Set<String> processedIds) throws IOException;
    public List<Map<String, Map<String, String>>>  processExcelFile(File excelFile, String diseaseClass, int institutionId) throws IOException ;
    public List<Map<String, Map<String, String>>>  processServerExcelFile(File excelFile, String diseaseClass, int institutionId, Set<String> processedIds,Set<String> passIdsSet) throws IOException ;
}
