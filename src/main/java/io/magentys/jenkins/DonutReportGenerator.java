package io.magentys.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import io.magentys.donut.gherkin.Generator;
import io.magentys.donut.gherkin.model.ReportConsole;

import java.io.File;
import java.io.IOException;

import jenkins.tasks.SimpleBuildStep;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

@SuppressWarnings("unchecked")
public class DonutReportGenerator extends Recorder implements SimpleBuildStep {
    private static final String[] DEFAULT_FILE_INCLUDES = new String[] { "**/*.json" };
    public final String sourceDirectory;
    public final boolean countSkippedAsFailure;
    public final boolean countPendingAsFailure;
    public final boolean countUndefinedAsFailure;
    public final boolean countMissingAsFailure;

    @DataBoundConstructor
    public DonutReportGenerator(String sourceDirectory, boolean countSkippedAsFailure, boolean countPendingAsFailure,
            boolean countUndefinedAsFailure,
            boolean countMissingAsFailure) {
        this.sourceDirectory = sourceDirectory;
        this.countSkippedAsFailure = countSkippedAsFailure;
        this.countPendingAsFailure = countPendingAsFailure;
        this.countUndefinedAsFailure = countUndefinedAsFailure;
        this.countMissingAsFailure = countMissingAsFailure;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath workspaceSourceDirectory = new FilePath(workspace, sourceDirectory);
        File outputDirectory = new File(build.getRootDir(), "donut");
        String buildName = build.getParent().getName();
        String buildNumber = Integer.toString(build.getNumber());

        Result result;
        if (build.getResult() == Result.ABORTED) {
            listener.getLogger()
                .println("[DonutReportGenerator] Skipping Donut report as build was aborted");
            result = Result.ABORTED;
        } else if (!hasResults(workspaceSourceDirectory)) {
            listener.getLogger()
                .println("[DonutReportGenerator] Skipping Donut report as no results found in: " + workspaceSourceDirectory);
            result = Result.NOT_BUILT;
        } else {
            try {
                listener.getLogger()
                    .println(
                            String.format("[DonutReportGenerator] Generating Donut Report for Job: %s and Build Number: %s", buildName,
                                    buildNumber));
                listener.getLogger().println("[DonutReportGenerator] Output directory: " + outputDirectory.getAbsolutePath());

                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs();
                }

                workspaceSourceDirectory.copyRecursiveTo(StringUtils.join(DEFAULT_FILE_INCLUDES, ','), new FilePath(outputDirectory));

                ReportConsole reportConsole = Generator.apply(outputDirectory.getAbsolutePath(), outputDirectory.getAbsolutePath(), "",
                        "", "default",
                        countSkippedAsFailure, countPendingAsFailure, countUndefinedAsFailure, countMissingAsFailure, buildName,
                        buildNumber);
                result = reportConsole.buildFailed() ? Result.FAILURE : Result.SUCCESS;
            } catch (Exception e) {
                result = Result.FAILURE;
                listener.getLogger().println("[DonutReportGenerator] An error occurred generating the report: " + e);
                for (StackTraceElement error : e.getStackTrace()) {
                    listener.getLogger().println(error);
                }
            }
        }

        build.addAction(new DonutBuildAction(build));
        build.setResult(result);
    }

    private boolean hasResults(FilePath workspaceSourceDirectory) throws IOException, InterruptedException {
        return workspaceSourceDirectory.act(new ResultsChecker());
    }

    private static class ResultsChecker implements FileCallable<Boolean> {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean invoke(File f, VirtualChannel channel) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setIncludes(DEFAULT_FILE_INCLUDES);
            scanner.setBasedir(f);
            scanner.scan();
            return scanner.getIncludedFilesCount() != 0;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
        }
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new DonutProjectAction(project);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Generate Donut report from results";
        }

        public FormValidation doCheckSourceDirectory(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) throws IOException {
            return project.getSomeWorkspace().validateRelativeDirectory(value);
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
