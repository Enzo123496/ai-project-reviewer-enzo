package fr.master.reviewer.ui;

import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.application.AnalysisRequest;
import fr.master.reviewer.application.ReviewerException;
import fr.master.reviewer.application.ReviewerFacade;
import fr.master.reviewer.config.AppConfig;
import fr.master.reviewer.persistence.HistoryEntry;
import fr.master.reviewer.project.Project;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Fenêtre principale (Swing). Elle ne contient AUCUNE logique métier : elle lit les choix de l'utilisateur,
 * appelle la ReviewerFacade et affiche ce qu'elle renvoie. Les traitements longs tournent hors du thread graphique.
 */
public final class MainWindow extends JFrame {

    private final ReviewerFacade facade;

    private final JTree tree = new JTree(new DefaultMutableTreeNode("Aucun projet chargé"));
    private final JComboBox<String> profileBox = new JComboBox<>();
    private final JComboBox<String> providerBox = new JComboBox<>();
    private final JComboBox<String> formatBox = new JComboBox<>();
    private final JCheckBox commentaryBox = new JCheckBox("Commentaire global par le LLM", true);
    private final Map<String, JCheckBox> criterionBoxes = new LinkedHashMap<>();
    private final JButton analyzeButton = new JButton("Lancer l'analyse");
    private final JButton reportButton = new JButton("Générer le rapport");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Prêt");
    private final ResultsTableModel resultsModel = new ResultsTableModel();
    private final JTable resultsTable = new JTable(resultsModel);
    private final JTextArea details = new JTextArea();
    private final JTextArea logArea = new JTextArea();
    private final JTextArea errorArea = new JTextArea();
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"Id", "Projet", "Date", "Profil", "Modèle", "Note /20", "Critères en échec"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTabbedPane tabs = new JTabbedPane();

    private Project project;
    private EvaluationResult lastResult;

    public MainWindow(ReviewerFacade facade) {
        super("AI Project Reviewer");
        this.facade = facade;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(1200, 780));
        setLocationRelativeTo(null);
        buildLayout();
        populateChoices();
        refreshHistory();
        updateButtons();
    }

    // ------------------------------------------------------------------ construction de l'écran

    private void buildLayout() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton openDir = new JButton("Ouvrir un dossier…");
        JButton openZip = new JButton("Ouvrir une archive .zip…");
        JButton openGit = new JButton("Dépôt Git…");
        openDir.addActionListener(e -> chooseProject(JFileChooser.DIRECTORIES_ONLY));
        openZip.addActionListener(e -> chooseProject(JFileChooser.FILES_ONLY));
        openGit.addActionListener(e -> {
            String url = JOptionPane.showInputDialog(this, "URL HTTPS du dépôt :");
            if (url != null && !url.isBlank()) {
                load(url.strip());
            }
        });
        toolbar.add(openDir);
        toolbar.add(openZip);
        toolbar.add(openGit);
        toolbar.addSeparator();
        toolbar.add(analyzeButton);
        toolbar.add(new JLabel("  Format : "));
        toolbar.add(formatBox);
        toolbar.add(reportButton);
        analyzeButton.addActionListener(e -> startAnalysis());
        reportButton.addActionListener(e -> generateReport());

        JPanel config = new JPanel();
        config.setLayout(new BoxLayout(config, BoxLayout.Y_AXIS));
        config.setBorder(BorderFactory.createTitledBorder("Configuration de l'analyse"));
        config.add(row("Profil :", profileBox));
        config.add(row("Modèle :", providerBox));
        config.add(row("", commentaryBox));
        JPanel criteriaPanel = new JPanel();
        criteriaPanel.setLayout(new BoxLayout(criteriaPanel, BoxLayout.Y_AXIS));
        for (Criterion c : facade.criteria()) {
            JCheckBox box = new JCheckBox(c.name() + "  (" + c.analyzer() + ")");
            box.setToolTipText(c.description());
            criterionBoxes.put(c.id(), box);
            criteriaPanel.add(box);
        }
        config.add(new JScrollPane(criteriaPanel));
        profileBox.addActionListener(e -> applyProfile());

        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tree), config);
        left.setResizeWeight(0.55);

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        logArea.setEditable(false);
        errorArea.setEditable(false);
        resultsTable.getSelectionModel().addListSelectionListener(e -> showDetails());
        JSplitPane resultsPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(resultsTable), new JScrollPane(details));
        resultsPane.setResizeWeight(0.45);
        JTable historyTable = new JTable(historyModel);
        historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && historyTable.getSelectedRow() >= 0) {
                    openFromHistory((String) historyModel.getValueAt(historyTable.getSelectedRow(), 0));
                }
            }
        });
        tabs.addTab("Résultats", resultsPane);
        tabs.addTab("Journal", new JScrollPane(logArea));
        tabs.addTab("Erreurs", new JScrollPane(errorArea));
        tabs.addTab("Historique", new JScrollPane(historyTable));

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
        main.setResizeWeight(0.35);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottom.add(status, BorderLayout.WEST);
        bottom.add(progress, BorderLayout.CENTER);

        getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(main, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
    }

    private static JPanel row(String label, java.awt.Component component) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (!label.isEmpty()) {
            p.add(new JLabel(label));
        }
        p.add(component);
        return p;
    }

    private void populateChoices() {
        facade.profiles().stream().map(AppConfig.Profile::name).forEach(profileBox::addItem);
        facade.providers().forEach(providerBox::addItem);
        providerBox.setSelectedItem(facade.defaultProvider());
        facade.reportFormats().forEach(formatBox::addItem);
        formatBox.setMaximumSize(formatBox.getPreferredSize());
        applyProfile();
    }

    private void applyProfile() {
        List<String> ids = facade.criteriaOfProfile((String) profileBox.getSelectedItem());
        criterionBoxes.forEach((id, box) -> box.setSelected(ids.contains(id)));
    }

    // ------------------------------------------------------------------ actions

    private void chooseProject(int mode) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(mode);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void load(String source) {
        status.setText("Chargement…");
        progress.setIndeterminate(true);
        new SwingWorker<Project, Void>() {
            @Override
            protected Project doInBackground() throws ReviewerException {
                return facade.loadProject(source);
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                try {
                    project = get();
                    tree.setModel(new DefaultTreeModel(ProjectTreeFactory.build(project)));
                    status.setText("Projet chargé : " + project.name());
                    log("Projet chargé : " + project.name() + " — " + project.countByType());
                } catch (Exception e) {
                    error("Chargement impossible : " + rootMessage(e));
                    status.setText("Échec du chargement");
                }
                updateButtons();
            }
        }.execute();
    }

    private void startAnalysis() {
        List<String> ids = criterionBoxes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Sélectionnez au moins un critère.");
            return;
        }
        resultsModel.setAll(List.of());
        details.setText("");
        lastResult = null;
        analyzeButton.setEnabled(false);
        reportButton.setEnabled(false);
        tabs.setSelectedIndex(0);
        AnalysisRequest request = new AnalysisRequest(project, (String) profileBox.getSelectedItem(), ids,
                (String) providerBox.getSelectedItem(), commentaryBox.isSelected());
        facade.analyzeAsync(request, new SwingAnalysisListener(this))
                .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> onAnalysisDone(result, failure)));
    }

    private void onAnalysisDone(EvaluationResult result, Throwable failure) {
        progress.setValue(progress.getMaximum());
        if (failure != null) {
            error("Analyse impossible : " + rootMessage(failure));
            status.setText("Analyse échouée");
        } else {
            lastResult = result;
            resultsModel.setAll(result.results());
            status.setText(String.format("Terminé : %.1f/20 — %d appel(s) LLM, %d échec(s)", result.overallScoreOn20(),
                    result.trace().llmCalls(), result.trace().llmFailures()));
            log("Synthèse : " + result.summary());
            refreshHistory();
        }
        updateButtons();
    }

    private void generateReport() {
        if (lastResult == null) {
            return;
        }
        String format = (String) formatBox.getSelectedItem();
        try {
            Path file = facade.generateReport(lastResult, format);
            log("Rapport écrit : " + file.toAbsolutePath());
            String message = "Rapport généré :\n" + file.toAbsolutePath();
            if ("latex".equals(format) && facade.pdfEnabled()) {
                message += facade.compilePdf(file).map(p -> "\nPDF : " + p.toAbsolutePath()).orElse("");
            }
            JOptionPane.showMessageDialog(this, message);
        } catch (ReviewerException e) {
            error(e.getMessage());
        }
    }

    private void openFromHistory(String id) {
        facade.historyResult(id).ifPresent(r -> {
            lastResult = r;
            resultsModel.setAll(r.results());
            tabs.setSelectedIndex(0);
            status.setText("Analyse " + id + " rechargée depuis l'historique");
            updateButtons();
        });
    }

    // ------------------------------------------------------------------ appelés par l'observateur (sur l'EDT)

    void onAnalysisStarted(int criteriaCount) {
        progress.setIndeterminate(false);
        progress.setMinimum(0);
        progress.setMaximum(criteriaCount);
        progress.setValue(0);
        status.setText("Analyse en cours…");
    }

    void onCriterionFinished(CriterionResult r, int index) {
        progress.setValue(index);
        resultsModel.add(r);
        if (r.status() == CriterionResult.Status.FAILED) {
            error(r.criterionName() + " : " + String.join(" ; ", r.issues()));
        }
    }

    void log(String message) {
        logArea.append(message + "\n");
    }

    void error(String message) {
        errorArea.append(message + "\n");
        tabs.setTitleAt(2, "Erreurs (!)");
    }

    // ------------------------------------------------------------------ affichage

    private void showDetails() {
        int row = resultsTable.getSelectedRow();
        if (row < 0 || row >= resultsModel.getRowCount()) {
            return;
        }
        CriterionResult r = resultsModel.get(row);
        StringBuilder sb = new StringBuilder();
        sb.append(r.criterionName()).append(" — ").append(r.score()).append('/').append(r.maxScore())
                .append(" (").append(r.status()).append(")\n").append(r.source()).append("\n");
        section(sb, "Points forts", r.strengths());
        section(sb, "Points faibles", r.weaknesses());
        section(sb, "Problèmes", r.issues());
        section(sb, "Recommandations", r.recommendations());
        section(sb, "Avertissements", r.warnings());
        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    private static void section(StringBuilder sb, String title, List<String> items) {
        if (!items.isEmpty()) {
            sb.append('\n').append(title).append(" :\n");
            items.forEach(i -> sb.append("  • ").append(i).append('\n'));
        }
    }

    private void refreshHistory() {
        historyModel.setRowCount(0);
        for (HistoryEntry h : facade.history()) {
            historyModel.addRow(new Object[]{h.analysisId(), h.projectName(), h.startedAt(), h.profileName(),
                    h.llmDescription(), h.overallScoreOn20(), h.failedCriteria()});
        }
    }

    private void updateButtons() {
        analyzeButton.setEnabled(project != null);
        reportButton.setEnabled(lastResult != null);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
