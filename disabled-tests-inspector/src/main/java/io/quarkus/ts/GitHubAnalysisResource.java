package io.quarkus.ts;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;

@Path("/analyze")
public class GitHubAnalysisResource {

    JsonFileProducer jsonFileProducer;

    public GitHubAnalysisResource(JsonFileProducer jsonFileProducer) {
        this.jsonFileProducer = jsonFileProducer;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String startAnalysis() {
        String repoOwner = System.getProperty("repoOwner", "org");
        String repoName = System.getProperty("repoName", "repo-name");
        String branches = System.getProperty("branches", "main,develop");
        String outputFile = System.getProperty("outputFile", "disabled-tests-report.json");
        System.out.println("Starting analysis for " + repoOwner + "/" + repoName + " on branches: " + branches);
        jsonFileProducer.produceJson(repoOwner, repoName, Arrays.asList(branches.split(",")), outputFile);
        return "Analysis started!";
    }
}

