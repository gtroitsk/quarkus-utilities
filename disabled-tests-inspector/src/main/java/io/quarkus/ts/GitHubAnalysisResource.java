package io.quarkus.ts;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;

@ApplicationScoped
public class GitHubAnalysisResource {

    @Inject
    JsonFileProducer jsonFileProducer;

    public void startAnalysis() {
        String repoOwner = System.getProperty("repoOwner", "org");
        String repoName = System.getProperty("repoName", "repo-name");
        String branches = System.getProperty("branches", "main");
        String outputFile = System.getProperty("outputFile", "disabled-tests-report.json");
        System.out.println("Starting analysis for " + repoOwner + "/" + repoName + " on branches: " + branches);
        jsonFileProducer.produceJson(repoOwner, repoName, Arrays.asList(branches.split(",")), outputFile);
        Quarkus.asyncExit(0);
    }
}

