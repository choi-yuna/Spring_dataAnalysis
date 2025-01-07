package com.fas.dentistry_data_analysis.dataAnlaysis.DTO;

import lombok.Data;

@Data
public class AnalysisRequestDTO {

    private String[] fileIds;
    private int institutionId;
    private String diseaseClass;
}
