package pl.kbaranski.hudson.jiraVersionRelease;

import java.net.URL;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This class describes single instance of JIRA issue tracker. It's quite
 * probable that single instance of JIRA will contains projects connected with
 * mamy different Jenkins / Hudson jobs so it's good idea to setup this data
 * only once.
 * 
 * @author Krzysztof Barański
 * @since 1.1
 */
public class TrackerInstance {

    /**
     * Name of JIRA instance. This values will be presented in select box on job
     * setup page.
     */
    private String instanceName;
    /** URL of JIRA ionstance. */
    private URL url;
    /** Name of the user that will be used to manage versions in JIRA. */
    private String user;
    /** Password to the account of the user specified in {@code user}. */
    private String pass;

    @DataBoundConstructor
    public TrackerInstance(String instanceName, URL url, String user, String pass) {
        this.instanceName = instanceName;
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }
}
