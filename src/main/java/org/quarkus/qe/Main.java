package org.quarkus.qe;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        AddedArtifactsPrint addedArtifactsPrint = new AddedArtifactsPrint();
        addedArtifactsPrint.printToFile();
    }
}
