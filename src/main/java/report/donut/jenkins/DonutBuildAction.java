package report.donut.jenkins;

import hudson.model.Run;

import java.io.File;

public class DonutBuildAction extends DonutAction {
    private final Run<?, ?> build;

    public DonutBuildAction(Run<?, ?> build) {
        super();
        this.build = build;
    }

    @Override
    protected String getTitle() {
        return this.build.getDisplayName() + " html3";
    }

    @Override
    protected File dir() {
        return new File(build.getRootDir(), "donut");
    }

}
