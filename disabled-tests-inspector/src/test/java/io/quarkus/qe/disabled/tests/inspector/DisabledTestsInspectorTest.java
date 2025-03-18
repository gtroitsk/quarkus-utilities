package io.quarkus.qe.disabled.tests.inspector;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class DisabledTestsInspectorTest {

    @Inject
    DisabledTestAnalyserService disabledTestAnalyserService;
    
    
    @Test
    public void shouldExtractClosedIssueLinkFromAnnotation() {
        String testClassContent = """
            @QuarkusScenario
            public class IssueTest {
                @Test
                @Disabled("https://github.com/quarkusio/quarkus/issues/39230")
                public void testMethod() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "IssueTest", "testMethod", "Disabled",
                "https://github.com/quarkusio/quarkus/issues/39230",
                "https://github.com/quarkusio/quarkus/issues/39230");
        assertTrue(results.get(0).isIssueClosed());
    }

    @Test
    public void shouldExtractIssueLinkFromCommentAboveTest() {
        String testClassContent = """
            @QuarkusScenario
            public class CommentIssueTest {
                @Test
                // Related issue: https://github.com/org/repo/issues/123
                @DisabledOnNative
                public void testMethod() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "CommentIssueTest", "testMethod",
                "DisabledOnNative", "Related issue: https://github.com/org/repo/issues/123",
                "https://github.com/org/repo/issues/123");
    }

    @Test
    public void shouldExtractEnabledAnnotationTests() {
        String testClassContent = """
            @QuarkusScenario
            public class EnabledTestClass {
                @Test
                @Enabled("Reason")
                public void testMethod() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "EnabledTestClass", "testMethod",
                "Enabled", "Reason", null);
    }

    @Test
    public void shouldHandleMissingReason() {
        String testClassContent = """
            @QuarkusScenario
            public class MissingReasonTest {
                @Disabled
                @Test
                public void testMethod() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "MissingReasonTest", "testMethod",
                "Disabled", null, null);
    }

    @Test
    public void shouldHandleDifferentMethodVisibilityLevels() {
        String testClassContent = """
            @QuarkusScenario
            public class VisibilityTest {
                @Disabled("Public method")
                @Test
                public void publicTest() {}

                @Disabled("Package-private method")
                @Test
                void packagePrivateTest() {}

                @Disabled("Protected method")
                @Test
                protected void protectedTest() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(3, results.size());

        assertDisabledTestDetails(results.get(0), "VisibilityTest", "publicTest",
                "Disabled", "Public method", null);

        assertDisabledTestDetails(results.get(1), "VisibilityTest", "packagePrivateTest",
                "Disabled", "Package-private method", null);

        assertDisabledTestDetails(results.get(2), "VisibilityTest", "protectedTest",
                "Disabled", "Protected method", null);
    }

    @Test
    public void shouldHandleClassLevelDisabledAnnotation() {
        String testClassContent = """
            @Disabled("Disabled class")
            public class ClassDisabledTest {
                @Test
                public void testMethod() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "ClassDisabledTest", "All tests in class",
                "Disabled", "Disabled class", null);
    }

    @Test
    public void shouldExtractReasonFromSameLineComment() {
        String testClassContent = """
        @QuarkusScenario
        public class CommentInlineTest {
            @DisabledOnNative // Reason from the comment
            @Test
            public void testMethod() {}
        }
        """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "CommentInlineTest", "testMethod",
                "DisabledOnNative", "Reason from the comment", null);
    }

    @Test
    public void shouldExtractIssueLinkFromReasonString() {
        String testClassContent = """
        @QuarkusScenario
        public class IssueInReasonTest {
            @Disabled("Wait for this to be fixed https://github.com/HtmlUnit/htmlunit/issues/232 or rewrite from HtmlUnit")
            @Test
            public void testMethod() {}
        }
        """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "IssueInReasonTest", "testMethod",
                "Disabled",
                "Wait for this to be fixed https://github.com/HtmlUnit/htmlunit/issues/232 or rewrite from HtmlUnit",
                "https://github.com/HtmlUnit/htmlunit/issues/232");
    }

    @Test
    public void shouldExtractImplicitDisabledReason() {
        String testClassContent = """
        @QuarkusScenario
        public class ImplicitReasonTest {
            @DisabledOnNative(reason = "Due to high native build execution time")
            @Test
            public void testMethod() {}
        }
        """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "ImplicitReasonTest", "testMethod",
                "DisabledOnNative", "Due to high native build execution time", null);
    }

    @Test
    public void shouldExtractImplicitDisabledReasonWithIssueLink() {
        String testClassContent = """
        @QuarkusScenario
        public class ImplicitReasonWithIssueTest {
            @DisabledIfSystemProperty(named = "profile.id", matches = "native", disabledReason = "Only for JVM mode, error in native mode - https://github.com/org/repo/issues/25928")
            @Test
            public void testMethod() {}
        }
        """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());
        assertDisabledTestDetails(results.get(0), "ImplicitReasonWithIssueTest", "testMethod",
                "DisabledIfSystemProperty",
                "Only for JVM mode, error in native mode - https://github.com/org/repo/issues/25928",
                "https://github.com/org/repo/issues/25928");
    }

    @Test
    public void shouldExtractMultipleDisabledTests() {
        String testClassContent = """
            @QuarkusScenario
            @DisabledOnNative
            public class MultiTestClass {
                @Disabled // No reason
                @Test
                public void testOne() {}

                @DisabledOnOs(value = OS.WINDOWS, disabledReason = "No lsof command on Windows")
                @EnabledIfSystemProperty(named = "profile.id", matches = "native", disabledReason = "https://github.com/org/repo/issues/25928")
                @Test
                public void testTwo() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(4, results.size());

        assertDisabledTestDetails(results.get(0), "MultiTestClass", "All tests in class",
                "DisabledOnNative", null, null);

        assertDisabledTestDetails(results.get(1), "MultiTestClass", "testOne",
                "Disabled", "No reason", null);

        assertDisabledTestDetails(results.get(2), "MultiTestClass", "testTwo",
                "DisabledOnOs", "No lsof command on Windows", null);

        assertDisabledTestDetails(results.get(3), "MultiTestClass", "testTwo",
                "EnabledIfSystemProperty",
                "https://github.com/org/repo/issues/25928", "https://github.com/org/repo/issues/25928");
    }

    @Test
    public void shouldExtractIssueLinkFromCommentSameLine() {
        String testClassContent = """
            @QuarkusScenario
            public class SameLineReasonClass {
                @DisabledOnOs(OS.WINDOWS) // enable me when https://github.com/org/repo/issues/35913 gets fixed
                @Test
                public void test() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(1, results.size());

        assertDisabledTestDetails(results.get(0), "SameLineReasonClass", "test",
                "DisabledOnOs",
                "enable me when https://github.com/org/repo/issues/35913 gets fixed",
                "https://github.com/org/repo/issues/35913");
    }

    @Test
    public void shouldExtractOwnDisabledReasonMultipleAnnotations() {
        String testClassContent = """
            @OpenShiftScenario
            @DisabledIfSystemProperty(named = "ts.arm.missing.services.excludes", matches = "true", disabledReason = "https://github.com/org/repo/issues/1145")
            @EnabledIfSystemProperty(named = "ts.redhat.registry.enabled", matches = "true")
            public class TestClass {
                @Test
                public void test() {}
            }
            """;

        List<DisabledTest> results = analyzeTestClass(testClassContent);

        assertEquals(2, results.size());

        assertDisabledTestDetails(results.get(0), "TestClass", "All tests in class",
                "DisabledIfSystemProperty",
                "https://github.com/org/repo/issues/1145",
                "https://github.com/org/repo/issues/1145");

        assertDisabledTestDetails(results.get(1), "TestClass", "All tests in class",
                "EnabledIfSystemProperty",
                null, null);
    }

    private List<DisabledTest> analyzeTestClass(String testClassContent) {
        Map<String, DisabledTestsModuleStats> moduleStats = new HashMap<>();
        TestClassData testFile = new TestClassData("https://gh.none/stub", "example/src/test/TestClass.java", testClassContent);
        return disabledTestAnalyserService.extractDisabledTests(testFile, moduleStats);
    }

    private void assertDisabledTestDetails(DisabledTest test, String className, String testName,
                                           String annotation, String reason, String issueLink) {
        assertEquals(className, test.getClassName());
        assertEquals(testName, test.getTestName());
        assertEquals(annotation, test.getAnnotationType());
        assertEquals(reason, test.getReason());
        assertEquals(issueLink, test.getIssueLink());
    }
}
