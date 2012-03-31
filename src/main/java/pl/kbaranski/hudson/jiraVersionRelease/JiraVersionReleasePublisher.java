package pl.kbaranski.hudson.jiraVersionRelease;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;

import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.rpc.ServiceException;

import org.kohsuke.stapler.DataBoundConstructor;

import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteAuthenticationException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteVersion;

public class JiraVersionReleasePublisher extends Notifier {

    private String instanceName;

    private String projectKey;

    private String prefixRegexp;

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final static Logger LOG = Logger.getLogger(JiraVersionReleasePublisher.class.getName());

    @DataBoundConstructor
    public JiraVersionReleasePublisher(String instanceName, String projectKey, String prefixRegexp) {
        this.instanceName = instanceName;
        this.projectKey = projectKey;
        this.prefixRegexp = prefixRegexp;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getPrefixRegexp() {
        return prefixRegexp;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public TrackerInstance getCurrentTracker() {
        if (this.instanceName == null)
            return null;
        for (TrackerInstance ti : DESCRIPTOR.getInstances()) {
            if (instanceName.equals(ti.getInstanceName())) {
                return ti;
            }
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

        JiraUtil jiraUtil = new JiraUtil(getCurrentTracker(), projectKey, prefixRegexp);

        try {
            jiraUtil.connect();
            RemoteVersion version = jiraUtil.getVersion(build.getNumber());
            jiraUtil.releaseVersion(version);
            listener.getLogger().println(
                    "JIRA: Wydano wersje " + version.getName() + " w projekcie " + this.getProjectKey());

            String newVerName = jiraUtil.getJiraVersionNamePrefix();
            if (newVerName != null) {
                // tworzymy nową wersję
                String newVersionFullName = newVerName + (build.getNumber() + 1);
                jiraUtil.createVersion(newVersionFullName);
                listener.getLogger().println(
                        "JIRA: Utworzono wersje " + newVersionFullName + " w projekcie " + this.getProjectKey());
                return true;
            } else {
                listener.getLogger().println(
                        "JIRA: W JIRA nie odnaleziono wersji odpowiadającej biezacemu numerowi kopilacji");
            }
            jiraUtil.disconnect();
        } catch (ServiceException e) {
            System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ")
                    .append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
        } catch (RemoteAuthenticationException e) {
            listener.getLogger().println("JIRA: Nie uwierzytelniono uzytkownika");
            System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ")
                    .append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
        } catch (RemoteException e) {
            System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ")
                    .append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
        } catch (java.rmi.RemoteException e) {
            System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ")
                    .append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
        } catch (Exception e) {
            System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ")
                    .append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
        }
        return false;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link JiraVersionReleasePublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See
     * <tt>views/hudson/plugins/hello_world/JiraVersionReleasePublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<TrackerInstance> instances = new CopyOnWriteList<TrackerInstance>();

        public DescriptorImpl() {
            super(JiraVersionReleasePublisher.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.displayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/jiraVersionRelease/help-config.html";
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        public void setInstances(TrackerInstance instance) {
            instances.add(instance);
        }

        public TrackerInstance[] getInstances() {
            return instances.toArray(new TrackerInstance[instances.size()]);
        }
    }
}
