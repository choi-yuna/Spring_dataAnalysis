package com.fas.dentistry_data_analysis.repository;

import com.fas.dentistry_data_analysis.entity.AnalysisResults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisResultsRepository extends JpaRepository<AnalysisResults, Long> {
}
