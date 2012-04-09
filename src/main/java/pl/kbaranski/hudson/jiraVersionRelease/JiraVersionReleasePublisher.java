/*
 * The MIT License
 * 
 * Copyright (c) 2010-2012, Krzysztof Barański.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package pl.kbaranski.hudson.jiraVersionRelease;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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
            if (instanceName.equals(ti.getName())) {
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
        } catch (JiraException e) {
            LOG.log(Level.SEVERE, "[JiraException] ", e);
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

        private static final String MY_PREFIX = "iraVersionReleasePublisher.";

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            JiraVersionReleasePublisher jpp = req.bindParameters(JiraVersionReleasePublisher.class, MY_PREFIX);
            if (jpp.instanceName == null) {
                jpp = null; // not configured
            }
            return jpp;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            LOG.info("configure...");
            for (Object o : formData.keySet()) {
                LOG.info("" + o.toString());
            }
            instances.replaceBy(req.bindParametersToList(TrackerInstance.class, MY_PREFIX));
            LOG.info("instances.size() : " + instances.size());
            save();
            return true;
        }

        public FormValidation doLogonCheck(StaplerRequest staplerRequest) {

            LOG.log(Level.INFO, "parametrow : " + staplerRequest.getParameterMap().size());
            for (Object s : staplerRequest.getParameterMap().keySet()) {
                LOG.log(Level.INFO, "param name : " + s);
            }
            String url = Util.fixEmpty(staplerRequest.getParameter("url"));
            String user = Util.fixEmpty(staplerRequest.getParameter("user"));
            String pass = Util.fixEmpty(staplerRequest.getParameter("pass"));
            LOG.log(Level.INFO, "Logon check. url = " + url + ", user = " + user + ", pass = " + pass);
            if (url == null || user == null) {
                // It's impossible to check without url or username
                FormValidation.ok();
            }

            try {
                URL url2 = new URL(url);
                JiraUtil jiraUtil = new JiraUtil(new TrackerInstance(null, url2, user, pass), null, null);
                jiraUtil.connect();
            } catch (MalformedURLException e) {
                LOG.log(Level.WARNING, "URL validation failed. Conversion to URL ends with " + e.getMessage());
                FormValidation.error(e.getMessage());
            } catch (JiraException e) {
                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage() != null && !"".equals(cause.getMessage())) {
                    return FormValidation.error(cause.getMessage());
                } else {
                    return FormValidation.error(e.getMessage());
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doUrlCheck(@QueryParameter final String value) {
            LOG.log(Level.INFO, "     URL validate");
            String url = Util.fixEmpty(value);
            if (url == null) {
                // Empty URL is not a bug...
                LOG.log(Level.INFO, "No URL specified :(");
                return FormValidation.ok();
            }
            try {
                URL url2 = new URL(url);
                JiraUtil jiraUtil = new JiraUtil(new TrackerInstance(null, url2, null, null), null, null);
                jiraUtil.connectNoLogin();
            } catch (MalformedURLException e) {
                LOG.log(Level.WARNING, "URL validation failed. Conversion to URL ends with " + e.getMessage());
                FormValidation.error(e.getMessage());
            } catch (JiraException e) {
                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage() != null && !"".equals(cause.getMessage())) {
                    return FormValidation.error(cause.getMessage());
                } else {
                    return FormValidation.error(e.getMessage());
                }
            }

            return FormValidation.ok();
        }
    }
}
