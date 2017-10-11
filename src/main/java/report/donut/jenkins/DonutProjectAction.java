package report.donut.jenkins;

import hudson.model.ProminentProjectAction;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Run;

import java.io.File;

public class DonutProjectAction extends DonutAction implements ProminentProjectAction {
    private final AbstractItem item;

    public DonutProjectAction(AbstractItem item) {
        super();
        this.item = item;
    }

    @Override
    protected File dir() {
        if (item instanceof AbstractProject) {
            Run<?, ?> run = getAbstractProject().getLastCompletedBuild();
            if (run != null) {
                File buildArchiveDir = getBuildArchiveDir(run);
                if (buildArchiveDir.exists()) {
                    return buildArchiveDir;
                }
            }
        }
        return getProjectArchiveDir(item);
    }

    private AbstractProject<?, ?> getAbstractProject() {
        return (AbstractProject<?, ?>) item;
    }

    private File getProjectArchiveDir(AbstractItem item) {
        return new File(item.getRootDir(), "donut");
    }

    private File getBuildArchiveDir(Run<?, ?> run) {
        return new File(run.getRootDir(), "donut");
    }

    @Override
    protected String getTitle() {
        return item.getDisplayName() + " html2";
    }
}