package fr.master.reviewer.project;

import java.util.List;

/**
 * FACTORY : choisit le bon chargeur selon la source. L'IHM ne connaît ni les ZIP ni Git,
 * elle transmet juste une chaîne. Nouveau type de source = nouvelle classe ajoutée à la liste.
 */
public final class ProjectLoaderFactory {

    private final List<ProjectLoader> loaders;

    public ProjectLoaderFactory(List<ProjectLoader> loaders) {
        this.loaders = List.copyOf(loaders);
    }

    public ProjectLoader loaderFor(String source) throws ProjectLoadException {
        return loaders.stream()
                .filter(l -> l.supports(source))
                .findFirst()
                .orElseThrow(() -> new ProjectLoadException("Source non reconnue : " + source));
    }

    public Project load(String source) throws ProjectLoadException {
        return loaderFor(source).load(source);
    }
}
