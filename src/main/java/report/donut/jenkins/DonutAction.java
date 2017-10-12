package report.donut.jenkins;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public abstract class DonutAction implements Action {

    private static final String DONUT_REPORT_FILENAME = "donut-report.html";

    public String getUrlName() {
        return "donut";
    }

    public String getDisplayName() {
        return "Donut Reporting";
    }

    public String getIconFileName() {
        return "/plugin/donut-jenkins-plugin/icons/donut.png";
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // Relax the Content Security Policy
        System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "");
        System.setProperty("jenkins.model.DirectoryBrowserSupport.CSP", "");

        DirectoryBrowserSupport dbs = new DirectoryBrowserSupport(this, new FilePath(this.dir()), this.getTitle(), getUrlName(), false);
        File report = new File(dir(), DONUT_REPORT_FILENAME);

        if (!report.exists()) {
            rsp.sendRedirect(Functions.getResourcePath() + "/plugin/donut-jenkins-plugin/error.html");
            return;
        }

        dbs.setIndexFileName(DONUT_REPORT_FILENAME);
        dbs.generateResponse(req, rsp, this);
    }

    protected abstract String getTitle();

    protected abstract File dir();

}
