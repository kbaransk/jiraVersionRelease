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

import javax.xml.rpc.ServiceException;

import org.kohsuke.stapler.DataBoundConstructor;

import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteAuthenticationException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteVersion;

/**
 * Main class of Hudson / Jenkins plugin that is responsible for
 * 
 * @author Krzysztof Barański
 * @since 1.0
 */
public class JiraVersionReleasePublisher extends Notifier {

    /** User-friendly name of the instance selected for specified job by user. */
    private String instanceName;

    /** Key that specifies project in JIRA. */
    private String projectKey;

    /** Regular expression that defines version name schema in JIRA. */
    private String prefixRegexp;

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /** Logger object. */
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

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public TrackerInstance getCurrentTracker() {
        if (this.instanceName == null) {
            return null;
        }
        for (TrackerInstance ti : DESCRIPTOR.getInstances()) {
            if (instanceName.equals(ti.getInstanceName())) {
                return ti;
            }
        }
        return null;
    }

    /**
     * This method is being invoked when build has finished and it's responsible
     * for marking as released JIRA version that matches that build and than
     * creating next version in JIRA (not released). {@inheritDoc}
     */
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
     * The class is marked as public so that it can be accessed from views. This
     * class is also responsible for global configuration (Hudson / Jenkins
     * wide).
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * List of JIRA server instances defined on Hudson / Jenkins global
         * configuration level. While configuring single job it will be possible
         * to choose one of these.
         */
        private final CopyOnWriteList<TrackerInstance> instances = new CopyOnWriteList<TrackerInstance>();

        /**
         * Constructor that loads current configuration.
         */
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

        /**
         * Adds instance to
         * {@link JiraVersionReleasePublisher.DescriptorImpl#instances} list.
         * 
         * @param instance
         *            JIRA instance data to add.
         */
        public void setInstances(TrackerInstance instance) {
            instances.add(instance);
        }

        /**
         * Returns {@link JiraVersionReleasePublisher.DescriptorImpl#instances}.
         * 
         * @return Returns
         *         {@link JiraVersionReleasePublisher.DescriptorImpl#instances}
         */
        public TrackerInstance[] getInstances() {
            return instances.toArray(new TrackerInstance[instances.size()]);
        }
    }
}
