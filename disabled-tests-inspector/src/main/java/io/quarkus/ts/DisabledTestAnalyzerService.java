package io.quarkus.ts;

import jakarta.enterprise.context.ApplicationScoped;
import org.kohsuke.github.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DisabledTestAnalyzerService {

    private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile(
            "\\bpublic\\s+class\\s+(\\w+)"
    );

    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "@Test\\s*\\n?\\s*public\\s+void\\s+(\\w+)\\s*\\("
    );

    private static final Pattern DISABLED_ANNOTATION_PATTERN = Pattern.compile(
            "@(Disabled\\w*)\\s*\\(?([^)]*)?\\)?"
    );

    private static final Pattern DISABLED_REASON_PATTERN = Pattern.compile(
            "reason\\s*=\\s*\"([^\"]+)\""
    );

    private static final Pattern DISABLED_ISSUE_LINK_PATTERN = Pattern.compile(
            "(https://github\\.com/[^\\s\"]+)"
    );

    private static final Pattern ISSUE_PATTERN = Pattern.compile(
            "(https://(?:github\\.com/.+?/issues/\\d+|issue\\.redhat\\.com/.+?/browse/\\w+-\\d+))"
    );


    public List<BranchAnalysisResult> analyzeRepository(String repoOwner, String repoName, List<String> branches) throws IOException {
        GitHub github = GitHub.connect(); // Auth via token from ~/.github or env
        GHRepository repo = github.getRepository(repoOwner + "/" + repoName);

        List<BranchAnalysisResult> results = new ArrayList<>();
        for (String branch : branches) {
            GHTree tree = repo.getTreeRecursive(branch, 1);
            List<DisabledTest> disabledTests = new ArrayList<>();

            for (GHTreeEntry entry : tree.getTree()) {
                if (entry.getPath().endsWith(".java")) {
                    String fileContent = repo.getFileContent(entry.getPath(), branch).getContent();
//                    System.out.println("Analyzing file: " + entry.getPath());
                    disabledTests.addAll(extractDisabledTests(entry.getPath(), fileContent));
                    // print disabled tests
                    disabledTests.forEach(System.out::println);
                }
            }
            results.add(new BranchAnalysisResult(branch, disabledTests));
        }
//        List<DisabledTest> disabledTests = new ArrayList<>();
//        disabledTests.addAll(extractDisabledTests("path", "content"));
//        results.add(new BranchAnalysisResult("branch", disabledTests));
        return results;
    }

    private List<DisabledTest> extractDisabledTests(String filePath, String fileContent) {

//        String fileContent = """
//    @CustomAnnotation
//    @DisabledOnNative(reason = "Due to high native build execution time")
//    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Netty Native Transport not supported on Windows, see https://quarkus.io/guides/vertx-reference#native-transport")
//    @CustomAnnotation
//    @DisabledIfSystemProperty(named = "profile.id", matches = "native", disabledReason = "Only for JVM mode, error in native mode - https://github.com/quarkusio/quarkus/issues/25928")
//    public class SampleTest {
//
//        @Test
//        @Disabled("https://github.com/q/issues/28421")
//        public void validateReturnValue() {}
//
//        //             This test won't work on Openshift as the request expect valid Host header
//        @DisabledSpecially("this is a reason")
//        public void anotherTest() {}
//
//        @Disabled
//        public void simpleDisabled() {
//            Response response = given().accept(ContentType.TEXT).get(REACTIVE_ENDPOINT_WITH_MULTIPLE_PRODUCES);
//            validate(response)
//                    .isBadRequest()
//                    .hasTextError();
//        }
//
//        @DisabledOnNative(reason = "Annotation @ClientHeaderParam not working in Native. Reported by https://github.com/quarkusio/quarkus/issues/13660")
//        public void disabledOnNative() {
//            someMethod();
//        }
//
//        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Reason1")
//        @DisabledOnFipsAndNative(reason = "https://issues.redhat.com/browse/QUARKUS-2812") // this type requires autorization to JIRA
//        public void disabledTwice() {
//            someMethod();
//        }
//
//        @DisabledOnNative         // Special reason
//        public void specialReason() {
//            someMethod();
//        }
//    }
//    """;

        List<DisabledTest> disabledTests = new ArrayList<>();
        String[] lines = fileContent.split("\n");

        String previousLine = "";
        String currentClass = "UnknownClass";
        String currentTestMethod = null;
        boolean insideDisabledBlock = false;
        List<String> annotationTypes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> issueLinks = new ArrayList<>();

        Pattern classPattern = Pattern.compile("public\\s+(?:\\w+\\s+)*class\\s+(\\w+)");
        Pattern testPattern = Pattern.compile("public\\s+void\\s+(\\w+)\\s*\\(");
        Pattern disabledPattern = Pattern.compile("@(Disabled\\w*)\\s*(?:\\((.*)\\))?");
        Pattern reasonPattern = Pattern.compile("reason\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);


        for (String currentLine : lines) {
            currentLine = currentLine.trim();

            Matcher classMatcher = classPattern.matcher(currentLine);
            if (classMatcher.find()) {
                System.out.println("Line: " + currentLine);
                System.out.println("Found class: " + classMatcher.group(1));
                currentClass = classMatcher.group(1);
            }

            Matcher testMethodMatcher = testPattern.matcher(currentLine);
            if (testMethodMatcher.find()) {
//                System.out.println("Found test method: " + testMethodMatcher.group(1));
                currentTestMethod = testMethodMatcher.group(1);
            }

            Matcher disabledMatcher = disabledPattern.matcher(currentLine);
            if (disabledMatcher.find()) {
                String reason = null;
                String issueLink = null;
                insideDisabledBlock = true;
                String annotationType = disabledMatcher.group(1);

//                System.out.println("Found disabled annotation: " + disabledMatcher.group(1));
//                System.out.println("Current class: " + currentClass);
//                System.out.println("Current test method: " + currentTestMethod);
//                System.out.println("Annotation type: " + annotationType);

                String annotationContent = disabledMatcher.group(2);
                if (annotationContent != null) {
                    System.out.println("Annotation content: " + annotationContent);
                    Matcher reasonMatcher = reasonPattern.matcher(annotationContent);

                    if (reasonMatcher.find()) {
                        reason = reasonMatcher.group(1);
                        System.out.println("Reason: " + reason);
                    }

                    issueLink = extractIssueLink(annotationContent);
                    System.out.println("Issue link: " + issueLink);
                }

                if (issueLink == null && previousLine.startsWith("//")) {
                    issueLink = extractIssueLink(previousLine);
                }

                // TODO: check if it is not break something
                if (issueLink == null && currentLine.startsWith("//")) {
                    issueLink = extractIssueLink(currentLine);
                }


                // If previous line is a comment, use it as the reason
                if (reason == null) {
                    reason = getDisablingReasonComment(previousLine);
                    System.out.println("Reason from previous line: " + reason);
                }

                if (currentLine.contains("//") && issueLink == null) {
                    if (reason == null) {
                        reason = getDisablingReasonComment(currentLine);
                        System.out.println("Reason from current line: " + reason);
                    }
                }

                annotationTypes.add(annotationType);
                reasons.add(reason);
                issueLinks.add(issueLink);
            }

//            System.out.println("-----------------------------------------");
//            System.out.println("Line: " + line);
//            System.out.println("Current class: " + currentClass);
//            System.out.println("Current test method: " + currentTestMethod);
//            System.out.println("Class found: " + classMatcher.lookingAt());
//            System.out.println("Inside disabled block: " + insideDisabledBlock);
//            System.out.println("Annotation type: " + annotationType);
//            System.out.println("Lookubng at test method: " + testMethodMatcher.lookingAt());
////            Thread.sleep(3000);
//            System.out.println("-----------------------------------------");
            if (insideDisabledBlock && (testMethodMatcher.lookingAt() || (classMatcher.lookingAt()))) {
                System.out.println("Finalizing disabled block");
                System.out.println("=========================================");

                for (int i = 0; i < annotationTypes.size(); i++) {
                    disabledTests.add(new DisabledTest(
                            currentTestMethod != null ? currentTestMethod : "All methods",
                            currentClass,
                            annotationTypes.get(i),
                            reasons.get(i),
                            issueLinks.get(i),
                            filePath,
                            isIssueClosed(issueLinks.get(i))
                    ));
                }

                annotationTypes.clear();
                reasons.clear();
                issueLinks.clear();
                insideDisabledBlock = false;
            }
            previousLine = currentLine;
        }

        disabledTests.forEach(System.out::println);

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

    private boolean isIssueClosed(String issueLink) {
        if (issueLink != null && issueLink.contains("github.com")) {
            try {
                String[] parts = issueLink.split("/");
                String repo = parts[parts.length - 3] + "/" + parts[parts.length - 2];
                int issueNumber = Integer.parseInt(parts[parts.length - 1]);

                GitHub github = GitHub.connect();
                GHIssue issue = github.getRepository(repo).getIssue(issueNumber);
                return issue.getState() == GHIssueState.CLOSED;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}