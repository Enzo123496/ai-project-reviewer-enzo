package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.CriterionResult;

import java.util.ArrayList;
import java.util.List;

/** Petit outil commun aux analyseurs déterministes : une liste de vérifications notées sur 10 points. */
final class Checklist {

    private double earned;
    private double possible;
    private final List<String> strengths = new ArrayList<>();
    private final List<String> weaknesses = new ArrayList<>();
    private final List<String> issues = new ArrayList<>();
    private final List<String> recommendations = new ArrayList<>();

    Checklist check(boolean ok, double points, String strength, String weakness, String recommendation) {
        possible += points;
        if (ok) {
            earned += points;
            if (strength != null) {
                strengths.add(strength);
            }
        } else {
            if (weakness != null) {
                weaknesses.add(weakness);
            }
            if (recommendation != null) {
                recommendations.add(recommendation);
            }
        }
        return this;
    }

    Checklist partial(double ratio, double points, String strength, String weakness, String recommendation) {
        double r = Math.max(0, Math.min(1, ratio));
        possible += points;
        earned += r * points;
        if (r >= 0.7 && strength != null) {
            strengths.add(strength);
        } else if (r < 0.7) {
            if (weakness != null) {
                weaknesses.add(weakness);
            }
            if (recommendation != null) {
                recommendations.add(recommendation);
            }
        }
        return this;
    }

    Checklist issue(String issue) {
        issues.add(issue);
        return this;
    }

    CriterionResult toResult(Criterion c, String source) {
        double ratio = possible == 0 ? 0 : earned / possible;
        double score = Math.round(ratio * c.maxScore() * 10) / 10.0;
        return new CriterionResult(c.id(), c.name(), score, c.maxScore(), strengths, weaknesses, issues,
                recommendations, CriterionResult.Status.OK, source, List.of(), 0);
    }
}
