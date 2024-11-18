package com.fas.dentistry_data_analysis.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FolderProcessorService {

    private final FileProcessor fileProcessor;

    @Autowired
    public FolderProcessorService(FileProcessor fileProcessor) {
        this.fileProcessor = fileProcessor;
    }


    public List<Map<String,String>> processFilesInFolder(String folderPath, String diseaseClass, int institutionId) throws IOException{
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Folder does not exist or is not a folder");
        }

        List<Map<String,String>> allData = new ArrayList<>();
        File[] files = folder.listFiles((dir,name) -> name.toLowerCase().endsWith(".xlsx"));


        if(files != null) {
            for(File file : files) {
                allData.addAll(fileProcessor.processExcelFile(file, diseaseClass, institutionId));
            }
        }
        return allData;
    }


}
