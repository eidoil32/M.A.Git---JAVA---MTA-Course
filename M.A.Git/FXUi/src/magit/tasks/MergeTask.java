package magit.tasks;

import controller.screen.intro.IntroController;
import controller.screen.main.MainController;
import exceptions.MyFileException;
import exceptions.RepositoryException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import magit.BlobMap;
import magit.Branch;
import magit.Magit;
import magit.utils.MergeProperty;
import settings.Settings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MergeTask extends Task<Void> {
    private Magit model;
    private Branch target;
    private MainController mainController;
    private MergeProperty mergeProperty = new MergeProperty(), conflictFinishProperty = new MergeProperty();
    private Map<String, BlobMap> changes;
    private BlobMap[] userApprove;

    public MergeTask(Branch target, MainController mainController) {
        this.model = mainController.getModel();
        this.target = target;
        this.mainController = mainController;
        this.userApprove = new BlobMap[2];
        for (int i = 0; i < 2; i++) this.userApprove[i] = new BlobMap(new HashMap<>());
    }

    @Override
    protected Void call() {
        try {
            updateStatus(Settings.language.getString("FINDING_ANCESTOR_COMMIT"), 0);
            //Commit ancestor = model.getAncestorCommit(target);

            updateStatus(Settings.language.getString("SEARCH_FOR_DIFFERENCES"), 1);
            try {
                changes = model.findChanges(target);
                if (changes.containsKey(Settings.FAST_FORWARD_MERGE)) {
                    model.fastForwardMerge(target);
                    updateStatus(Settings.language.getString("MERGE_FAST_FORWARD_MERGE"), 3);
                    return null;
                }
            } catch (RepositoryException | MyFileException e) {
                Platform.runLater(() -> IntroController.showError(e.getMessage()));
            }

            conflictFinishProperty.addListener(observable -> finishMerge());
            Platform.runLater(() -> mainController.mergeWindow(userApprove, changes, conflictFinishProperty));
        } catch (IOException e) {
            Platform.runLater(() -> IntroController.showError(e.getMessage()));
        }
        return null;
    }

    private void finishMerge() {
        if (conflictFinishProperty.get() != -1) {
            updateStatus(Settings.language.getString("START_MARGIN"), 2);

            mainController.commitCommentPopup(((observable, oldValue, newValue) -> {
                try {
                    model.merge(changes, userApprove, target.getCommit(), newValue);
                } catch (IOException | MyFileException | RepositoryException e) {
                    updateStatus(Settings.language.getString("MERGE_FAILED"), 3);
                    Platform.runLater(() -> IntroController.showError(e.getMessage()));
                }
                updateStatus(Settings.language.getString("MERGE_COMPLETED_SUCCESSFULLY"), 3);
                Platform.runLater(()->mainController.refreshData());
            }));
        } else if (conflictFinishProperty.isInError()) {
            updateStatus(Settings.language.getString("MARGE_CANCELED"), 3);
        }
    }

    private void updateStatus(String message, int position) {
        updateProgress(position, 3);
        updateMessage(message);
    }
}
