package io.quarkus.qe.disabled.tests.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class DisabledTestAnalyserService {

    private static final Logger LOG = Logger.getLogger(DisabledTestAnalyserService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:\\w+\\s+)*class\\s+(\\w+)"
    );

    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?:public|protected)?\\s*void\\s+(\\w+)\\s*\\("
    );

    private static final Pattern DISABLED_ANNOTATION_PATTERN = Pattern.compile(
            "@(Disabled\\w*|Enabled\\w*)\\s*(?:\\((.*)\\))?"
    );

    private static final Pattern REASON_PATTERN = Pattern.compile(
            "reason\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EXPLICIT_REASON_PATTERN = Pattern.compile(
            "^\"(.*)\"$"
    );

    private static final Pattern ISSUE_PATTERN = Pattern.compile(
    "(https://(?:github\\.com/.+?/issues/\\d+|issues\\.redhat\\.com/browse/\\w+-\\d+))"
    );


    public void analyzeRepository(String repoOwner, String repoName, List<String> branches, String baseOutputFileName) throws IOException {
        GitHub github = GitHub.connect();
        GHRepository repo = github.getRepository(repoOwner + "/" + repoName);

        for (String branch : branches) {
            LOG.infof("Analyzing repository '%s' on branch '%s'", repoName, branch);
            GHTree tree = repo.getTreeRecursive(branch, 1);
            List<DisabledTest> disabledTests = new ArrayList<>();
            Map<String, DisabledTestsModuleStats> moduleStats = new HashMap<>();

            int fileCount = 0;
            for (GHTreeEntry entry : tree.getTree()) {
                String filePath = entry.getPath();
                if (entry.getPath().contains("/test/") && filePath.endsWith(".java")) {
                    fileCount++;
                    if (fileCount % 10 == 0) {
                        LOG.infof("Processed %s test files...", fileCount);
                    }

                    GHContent testClassContent = repo.getFileContent(filePath, branch);
                    TestClassData data = new TestClassData(
                            testClassContent.getHtmlUrl(),
                            filePath,
                            readFileContent(testClassContent.read())
                    );
                    disabledTests.addAll(extractDisabledTests(data, moduleStats));
                }
            }

            BranchAnalysisResult result = new BranchAnalysisResult(branch, disabledTests);
            String testListFile = getTestFileName(baseOutputFileName, branch);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(testListFile), result);

            String statsListFile = getStatsFileName(baseOutputFileName, branch);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(statsListFile), moduleStats);

            LOG.infof("Analysis completed for branch '%s'. Found %s disabled tests.", branch, disabledTests.size());
        }
    }

    List<DisabledTest> extractDisabledTests(TestClassData testClassData,
                                            Map<String, DisabledTestsModuleStats> moduleStats) {

        List<DisabledTest> disabledTests = new ArrayList<>();
        String[] lines = testClassData.content().split("\n");

        String previousLine = "";
        String currentClass = null;
        String currentTestMethod = null;
        List<String> annotationTypes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> issueLinks = new ArrayList<>();
        boolean insideDisabledBlock = false;

        for (String currentLine : lines) {
            currentLine = currentLine.trim();

            Matcher classMatcher = CLASS_DECLARATION_PATTERN.matcher(currentLine);
            if (currentClass == null && classMatcher.find()) {
                currentClass = classMatcher.group(1);
            }

            Matcher testMethodMatcher = TEST_METHOD_PATTERN.matcher(currentLine);
            if (testMethodMatcher.find()) {
                currentTestMethod = testMethodMatcher.group(1);
            }

            Matcher disabledMatcher = DISABLED_ANNOTATION_PATTERN.matcher(currentLine);
            if (disabledMatcher.find()) {
                String reason = null;
                String issueLink = null;
                insideDisabledBlock = true;
                String annotationType = disabledMatcher.group(1);

                String annotationContent = disabledMatcher.group(2);
                if (annotationContent != null) {
                    Matcher reasonMatcher = REASON_PATTERN.matcher(annotationContent);

                    if (reasonMatcher.find()) {
                        reason = reasonMatcher.group(1);
                    }

                    if (reason == null) {
                        Matcher explicitReasonmatcher = EXPLICIT_REASON_PATTERN.matcher(annotationContent);
                        if (explicitReasonmatcher.find()) {
                            reason = explicitReasonmatcher.group(1);
                        }
                    }
                    issueLink = extractIssueLink(annotationContent);
                }

                Matcher previousLineAnnotationMatcher = DISABLED_ANNOTATION_PATTERN.matcher(previousLine);
                boolean hasPreviousLineAnnotation = previousLineAnnotationMatcher.find();

                // Don't allow to extract issue link from the annotation on previous line
                if (issueLink == null && previousLine.startsWith("//") && !hasPreviousLineAnnotation) {
                    issueLink = extractIssueLink(previousLine);
                }

                if (reason == null && !hasPreviousLineAnnotation) {
                    reason = getDisablingReasonComment(previousLine);
                }

                if (reason == null) {
                    reason = getDisablingReasonComment(currentLine);
                }

                // If an issue link hasn't been extracted yet, try extracting it from the reason comment
                if (issueLink == null && reason != null) {
                    issueLink = extractIssueLink(reason);
                }

                annotationTypes.add(annotationType);
                reasons.add(reason);
                issueLinks.add(issueLink);
            }

            if (insideDisabledBlock && (testMethodMatcher.lookingAt() || (classMatcher.lookingAt()))) {
                for (int i = 0; i < annotationTypes.size(); i++) {
                    disabledTests.add(new DisabledTest(
                            currentTestMethod != null ? currentTestMethod : "All tests in class",
                            currentClass,
                            annotationTypes.get(i),
                            reasons.get(i),
                            issueLinks.get(i),
                            testClassData.fileUrl(),
                            isGitHubIssueClosed(issueLinks.get(i))
                    ));

                    String moduleName = extractModuleName(testClassData.filePath());
                    moduleStats.putIfAbsent(moduleName, new DisabledTestsModuleStats());
                    moduleStats.get(moduleName).incrementAnnotation(annotationTypes.get(i));
                }

                annotationTypes.clear();
                reasons.clear();
                issueLinks.clear();
                insideDisabledBlock = false;
            }
            previousLine = currentLine;
        }
        return disabledTests;
    }

    private String extractIssueLink(String text) {
        Matcher issueMatcher = ISSUE_PATTERN.matcher(text);
        return issueMatcher.find() ? issueMatcher.group(1) : null;
    }

    private String getDisablingReasonComment(String line) {
        if (line.contains("//")) {
            return line.substring(line.indexOf("//") + 2).trim();
        }
        return null;
    }

    private boolean isGitHubIssueClosed(String issueLink) {
        if (issueLink != null && issueLink.contains("github.com")) {
            try {
                String[] parts = issueLink.split("/");
                String repo = parts[parts.length - 4] + "/" + parts[parts.length - 3];
                int issueNumber = Integer.parseInt(parts[parts.length - 1]);

                GitHub github = GitHub.connect();
                GHIssue issue = github.getRepository(repo).getIssue(issueNumber);
                return issue.getState() == GHIssueState.CLOSED;
            } catch (Exception e) {
                LOG.error("Failed to connect to GitHub and retrieve issue state", e);
                return false;
            }
        }
        return false;
    }

    private String readFileContent(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private String extractModuleName(String filePath) {
        return filePath.substring(0, filePath.indexOf("/src"));
    }

    private String getTestFileName(String fileName, String branchName) {
        return getFileName(fileName, branchName, "");
    }

    private String getStatsFileName(String fileName, String branchName) {
        return getFileName(fileName, branchName, "-stats");
    }

    private String getFileName(String fileName, String branchName, String fileNameSuffix) {
        return fileName.replaceFirst("(\\.json)?$", "-" + branchName + fileNameSuffix + ".json");
    }
}
