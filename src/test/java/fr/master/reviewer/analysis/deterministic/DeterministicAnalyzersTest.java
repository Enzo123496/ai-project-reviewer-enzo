package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicAnalyzersTest {

    @Test
    void badDockerfileScoresLow(@TempDir Path dir) {
        Project p = TestData.load(TestData.writeProject(dir, Map.of("Dockerfile",
                "FROM openjdk:latest\nENV DB_PASSWORD=admin123\nCOPY . /app\n")));
        CriterionResult r = new DockerfileAnalyzer().analyze(TestData.context(p, TestData.criterion("docker", "dockerfile"), AnalysisListener.none()));

        assertTrue(r.score() <= 3, "score obtenu : " + r.score());
        assertTrue(r.issues().get(0).contains("Secret"));
    }

    @Test
    void goodDockerfileScoresHigh(@TempDir Path dir) {
        Project p = TestData.load(TestData.writeProject(dir, Map.of(
                ".dockerignore", "target",
                "Dockerfile", "FROM maven:3.9-eclipse-temurin-21 AS build\nRUN mvn package\n"
                        + "FROM eclipse-temurin:21-jre\nRUN useradd app\nUSER app\nHEALTHCHECK CMD true\n")));
        CriterionResult r = new DockerfileAnalyzer().analyze(TestData.context(p, TestData.criterion("docker", "dockerfile"), AnalysisListener.none()));
        assertEquals(10.0, r.score());
    }

    @Test
    void noTestsGivesZero(@TempDir Path dir) {
        Project p = TestData.load(TestData.writeProject(dir, Map.of("src/main/java/A.java", "class A {}")));
        CriterionResult r = new TestPresenceAnalyzer().analyze(TestData.context(p, TestData.criterion("tests", "test-presence"), AnalysisListener.none()));
        assertEquals(0.0, r.score());
    }

    @Test
    void wellTestedProjectScoresHigh(@TempDir Path dir) {
        Project p = TestData.load(TestData.writeProject(dir, Map.of(
                "pom.xml", "<artifactId>junit-jupiter</artifactId>",
                "src/main/java/A.java", "class A {}",
                "src/test/java/ATest.java", "@Test void a(){ assertEquals(1,1); assertTrue(true);} @Test void b(){ assertThrows(X.class, null); assertNotNull(1);}")));
        CriterionResult r = new TestPresenceAnalyzer().analyze(TestData.context(p, TestData.criterion("tests", "test-presence"), AnalysisListener.none()));
        assertEquals(10.0, r.score());
    }
}
