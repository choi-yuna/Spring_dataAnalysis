package com.fas.dentistry_data_analysis.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JsonFileProcessor {

    List<Map<String, Map<String, String>>> processServerJsonFile( File jsonFile, String diseaseClass, int institutionId) throws IOException;
    public List<Map<String, Map<String, String>>> processJsonFile(File file, String diseaseClass, int institutionId) throws IOException;
}
