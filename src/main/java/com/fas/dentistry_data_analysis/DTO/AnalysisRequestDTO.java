package com.fas.dentistry_data_analysis.DTO;

import lombok.Data;

@Data
public class AnalysisRequestDTO {

    private String fileId;
    private int institutionId;
    private String diseaseClass;
}
