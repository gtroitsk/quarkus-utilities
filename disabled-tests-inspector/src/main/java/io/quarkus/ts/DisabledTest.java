package io.quarkus.ts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DisabledTest {
    @JsonProperty("test_name")
    private String testName;

    @JsonProperty("class_name")
    private String className;

    @JsonProperty("annotation_type")
    private String annotationType;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("issue_link")
    private String issueLink;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("can_be_reenabled")
    private boolean canBeReenabled;

    public DisabledTest(String testName, String className, String annotationType, String reason, String issueLink, String filePath, boolean canBeReenabled) {
        this.testName = testName;
        this.className = className;
        this.annotationType = annotationType;
        this.reason = reason;
        this.issueLink = issueLink;
        this.filePath = filePath;
        this.canBeReenabled = canBeReenabled;
    }

    public String getTestName() {
        return testName;
    }

    public String getClassName() {
        return className;
    }

    public String getAnnotationType() {
        return annotationType;
    }

    public String getReason() {
        return reason;
    }

    public String getIssueLink() {
        return issueLink;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean canBeReenabled() {
        return canBeReenabled;
    }
}