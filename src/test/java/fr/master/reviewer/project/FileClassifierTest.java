package fr.master.reviewer.project;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileClassifierTest {

    private final FileClassifier classifier = FileClassifier.defaultClassifier();

    @ParameterizedTest
    @CsvSource({
            "src/main/java/a/Foo.java, JAVA_SOURCE",
            "src/test/java/a/FooTest.java, TEST",
            "pom.xml, BUILD",
            "build.gradle.kts, BUILD",
            "Dockerfile, DOCKER",
            "docker/Dockerfile.dev, DOCKER",
            "README.md, DOCUMENTATION",
            "scripts/run.sh, SCRIPT",
            "src/main/resources/app.properties, RESOURCE",
            "config/app.yml, CONFIG",
            "data.bin, OTHER"
    })
    void classifiesByPath(String path, FileType expected) {
        assertEquals(expected, classifier.classify(path));
    }
}
