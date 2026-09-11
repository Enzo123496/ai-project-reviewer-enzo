package fr.master.reviewer.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mesures DÉTERMINISTES calculées sans LLM (toujours le même résultat pour le même projet).
 * Elles sont affichées dans le rapport ET injectées dans les prompts comme "faits" :
 * c'est notre manière de combiner analyse déterministe et analyse par IA.
 */
public record ProjectMetrics(int totalFiles, int javaFiles, int testFiles, long javaLines,
                             String largestJavaFile, int largestJavaFileLines, int emptyCatchBlocks,
                             int genericCatches, int todoCount, boolean hasReadme, boolean hasDockerfile,
                             boolean hasBuildFile) {

    public Map<String, String> asFacts() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("Total files", String.valueOf(totalFiles));
        facts.put("Java source files", String.valueOf(javaFiles));
        facts.put("Test files", String.valueOf(testFiles));
        facts.put("Java lines of code", String.valueOf(javaLines));
        facts.put("Largest Java file", largestJavaFile + " (" + largestJavaFileLines + " lines)");
        facts.put("Empty catch blocks", String.valueOf(emptyCatchBlocks));
        facts.put("catch (Exception|Throwable) occurrences", String.valueOf(genericCatches));
        facts.put("TODO/FIXME markers", String.valueOf(todoCount));
        facts.put("README present", String.valueOf(hasReadme));
        facts.put("Dockerfile present", String.valueOf(hasDockerfile));
        facts.put("Build file present", String.valueOf(hasBuildFile));
        return facts;
    }
}
