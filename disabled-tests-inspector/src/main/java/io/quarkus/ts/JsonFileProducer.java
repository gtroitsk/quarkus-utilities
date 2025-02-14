package io.quarkus.ts;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;

@ApplicationScoped
public class JsonFileProducer {

    @Inject
    DisabledTestAnalyzerService analyzerService;

    public void produceJson(String repoOwner, String repoName, List<String> branches, String outputFilePath) {
        try {
            List<BranchAnalysisResult> results = analyzerService.analyzeRepository(repoOwner, repoName, branches);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFilePath), results);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
