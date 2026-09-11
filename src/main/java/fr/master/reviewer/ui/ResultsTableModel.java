package fr.master.reviewer.ui;

import fr.master.reviewer.analysis.CriterionResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Modèle de tableau Swing : adapte une liste de CriterionResult à un JTable (aucune logique métier). */
final class ResultsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Critère", "Note", "Max", "Statut", "Méthode"};
    private final List<CriterionResult> rows = new ArrayList<>();

    void add(CriterionResult r) {
        rows.add(r);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    void setAll(List<CriterionResult> results) {
        rows.clear();
        rows.addAll(results);
        fireTableDataChanged();
    }

    CriterionResult get(int row) {
        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        CriterionResult r = rows.get(row);
        return switch (column) {
            case 0 -> r.criterionName();
            case 1 -> r.status().isScored() ? r.score() : "—";
            case 2 -> r.maxScore();
            case 3 -> r.status();
            default -> r.source();
        };
    }
}
