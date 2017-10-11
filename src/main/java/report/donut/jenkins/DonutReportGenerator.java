package report.donut.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import io.magentys.donut.gherkin.Generator;
import io.magentys.donut.gherkin.model.ReportConsole;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.tools.ant.DirectoryScanner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import scala.collection.JavaConverters;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("unchecked")
public class DonutReportGenerator extends Recorder implements SimpleBuildStep {

    private static final String[] DEFAULT_FILE_INCLUDES = new String[] { "**/*.json" };
    public final String sourceDirectory;
    public final boolean countSkippedAsFailure;
    public final boolean countPendingAsFailure;
    public final boolean countUndefinedAsFailure;
    public final boolean countMissingAsFailure;
    public final String customAttributes;

    @DataBoundConstructor
    public DonutReportGenerator(String sourceDirectory, boolean countSkippedAsFailure, boolean countPendingAsFailure, boolean countUndefinedAsFailure,
            boolean countMissingAsFailure, String customAttributes) {
        this.sourceDirectory = sourceDirectory;
        this.countSkippedAsFailure = countSkippedAsFailure;
        this.countPendingAsFailure = countPendingAsFailure;
        this.countUndefinedAsFailure = countUndefinedAsFailure;
        this.countMissingAsFailure = countMissingAsFailure;
        this.customAttributes = customAttributes;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        FilePath workspaceSourceDirectory = new FilePath(workspace, sourceDirectory);
        File outputDirectory = new File(build.getRootDir(), "donut");
        String buildName = build.getParent().getName();
        String buildNumber = Integer.toString(build.getNumber());

        Result result;
        if (build.getResult() == Result.ABORTED) {
            listener.getLogger().println("[DonutReportGenerator] Skipping Donut report as build was aborted");
            result = Result.ABORTED;
        } else if (!hasResults(workspaceSourceDirectory)) {
            listener.getLogger().println("[DonutReportGenerator] Skipping Donut report as no results found in: " + workspaceSourceDirectory);
            result = Result.NOT_BUILT;
        } else {
            try {
                EnvVars envVars = collectEnvVars(build, workspace, listener);

                listener.getLogger().println(
                        String.format("[DonutReportGenerator] Generating Donut Report for Job: %s and Build Number: %s", buildName, buildNumber));
                listener.getLogger().println("[DonutReportGenerator] Output directory: " + outputDirectory.getAbsolutePath());

                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs();
                }

                workspaceSourceDirectory.copyRecursiveTo(StringUtils.join(DEFAULT_FILE_INCLUDES, ','), new FilePath(outputDirectory));

                ReportConsole reportConsole = Generator
                        .apply(outputDirectory.getAbsolutePath(), outputDirectory.getAbsolutePath(), "", "", "default", countSkippedAsFailure,
                                countPendingAsFailure, countUndefinedAsFailure, countMissingAsFailure, buildName, buildNumber,
                                expandCustomAttributes(envVars));
                listener.getLogger().println("[DonutReportGenerator] Completed generating Donut Report");
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

    private scala.collection.mutable.Map<String, String> expandCustomAttributes(EnvVars envVars) throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : customAttributeProperties().entrySet()) {
            String value = entry.getValue().toString().replaceAll("[${}]+", "");
            map.put(entry.getKey().toString(), envVars.containsKey(value) ? envVars.get(value) : envVars.expand(value));
        } return JavaConverters.mapAsScalaMapConverter(map).asScala();
    }

    private EnvVars collectEnvVars(Run<?, ?> build, FilePath workspace, TaskListener listener)
            throws IOException, InterruptedException, XmlPullParserException {
        EnvVars envVars = build.getEnvironment(listener);
        FilePath filePath = workspace.child("pom.xml");
        if (filePath.exists()) {
            Properties mavenProperties = new MavenXpp3Reader().read(filePath.read()).getProperties();
            for (Map.Entry<Object, Object> entry : mavenProperties.entrySet()) {
                envVars.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return envVars;
    }

    private Properties customAttributeProperties() throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(customAttributes.replaceAll("[ \t]+", "\\\\ ")));
        return p;
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
