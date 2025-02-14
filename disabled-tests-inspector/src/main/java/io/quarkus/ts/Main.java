package io.quarkus.ts;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;


@QuarkusMain
public class Main {
    public static void main(String... args) {
        Quarkus.run(AnalyserApp.class, args);
        Quarkus.asyncExit(0);
    }

    public static class AnalyserApp implements QuarkusApplication {

        @Inject
        GitHubAnalysisResource gitHubAnalysisResource;

        @Override
        public int run(String... args) throws Exception {
            gitHubAnalysisResource.startAnalysis();
            Quarkus.waitForExit();
            System.exit(0);
            return 0;
        }
    }
}
