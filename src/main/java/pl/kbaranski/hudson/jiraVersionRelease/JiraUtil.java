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

import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.rpc.ServiceException;

import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceServiceLocator;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteVersion;

/**
 * This class is responsible for encapsulating all JIRA SOAP related logic.
 * 
 * @author Krzysztof Barański
 * @since 1.1
 */
public class JiraUtil {
    /** Logger object. */
    private final static Logger LOG = Logger.getLogger(JiraUtil.class.getName());

    /**
     * Prefix of version name in JIRA. Common prefix of version name format is
     * defined by regular expression and this is the part of name that matches
     * it.
     * 
     * @see JiraUtil#prefixRegexp
     */
    private String jiraVersionNamePrefix;

    /** Regular expression defining prefix of version name in JIRA. */
    private String prefixRegexp;
    /**
     * Key defining project in JIRA, for which operations should be done. Key is
     * used in JIRA as part of issue number.
     */
    private String projectKey;

    /**
     * Object that keeps data required to connect to JIRA instance. Contains
     * URL, username, etc.
     */
    private TrackerInstance trackerInstance;

    /** Object used to connect to JIRA SOAP services. */
    private JiraSoapService soapService;

    /** Token used to identify connection with JIRA SOAP service. */
    private String soapToken;

    /**
     * Creates object. Stores valuable date to use it in the future. When object
     * will be created, {@link JiraUtil#connect()} should be the first method
     * invoked on it.
     * 
     * @param trackerInstance
     *            Specified data required to connect to JIRA SOAP service.
     * @param projectKey
     *            Unique key of project in JIRA.
     * @param prefixRegexp
     *            Regular expression that defines format of names of versions in
     *            JIRA.
     */
    public JiraUtil(TrackerInstance trackerInstance, String projectKey, String prefixRegexp) {
        this.trackerInstance = trackerInstance;
        this.projectKey = projectKey;
        this.prefixRegexp = prefixRegexp;
    }

    public void connectNoLogin() throws JiraException {
        JiraSoapServiceService serviceLocator = new JiraSoapServiceServiceLocator();
        // Podłączenie do JIRA
        try {
            soapService = serviceLocator.getJirasoapserviceV2(trackerInstance.getUrl());
        } catch (ServiceException e) {
            LOG.log(Level.SEVERE, e.getClass().getName() + " while getting JIRA service.", e);
            throw new JiraException(e);
        }
    }

    /**
     * Logg in to JIRA SOAP service.
     * 
     * @throws JiraException
     */
    public void connect() throws JiraException {
        try {
            if (soapService == null) {
                connectNoLogin();
            }
            soapToken = soapService.login(trackerInstance.getUser(), trackerInstance.getPass());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getClass().getName() + " while connecting to JIRA service.", e);
            throw new JiraException(e);
        }
    }

    /**
     * Creates version in JIRA tracker with name specified in {@code fullName}
     * parameter.
     * 
     * @param fullName
     *            Name of version to create in JIRA.
     * @throws JiraException
     */
    public void createVersion(String fullName) throws JiraException {
        // tworzymy nową wersję
        RemoteVersion newVer = new RemoteVersion();
        newVer.setName(fullName);
        try {
            soapService.addVersion(soapToken, projectKey, newVer);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getClass().getName() + " while creating version in JIRA.", e);
            throw new JiraException(e);
        }
    }

    /**
     * Logouts from JIRA.
     * 
     * @throws JiraException
     */
    public void disconnect() throws JiraException {
        try {
            soapService.logout(soapToken);
        } catch (java.rmi.RemoteException e) {
            LOG.log(Level.SEVERE, "RemoteException while disconnecting from JIRA service.", e);
            throw new JiraException(e);
        }
    }

    /**
     * Returns prefix of version name from JIRA build. Proper value will be
     * returned after call to {@link JiraUtil#getVersion(int)} method.
     * 
     * @see JiraUtil#jiraVersionNamePrefix
     * @return
     */
    public String getJiraVersionNamePrefix() {
        return jiraVersionNamePrefix;
    }

    /**
     * Gets version that matches specified build from Hudson / Jenkins.
     * 
     * @param buildNumber
     *            Number of Hudson / Jenkins build.
     * @return Matching version if found, {@code null} otherwise.
     * @throws JiraException
     */
    public RemoteVersion getVersion(int buildNumber) throws JiraException {
        Pattern versionNumberPrefixPattern = Pattern.compile(prefixRegexp);
        String versionNumberFullRegex = prefixRegexp + buildNumber;

        try {
            for (RemoteVersion version : soapService.getVersions(soapToken, this.projectKey)) {
                // Z wersjami wydanymi nic nie chcemy robic
                if (version.isReleased() || version.isArchived()) {
                    continue;
                }
                // Pomijamy również wersje nie pasujące do wzorca
                if (!Pattern.matches(versionNumberFullRegex, version.getName())) {
                    continue;
                }
                // Teraz wiemy już, że znaleziona wersja, to ta odpowiadająca
                // poprzednio zbudowanej wersji w Hudson
                Matcher matcher = versionNumberPrefixPattern.matcher(version.getName());
                if (matcher.find()) {
                    jiraVersionNamePrefix = matcher.group();
                    return version;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getClass().getName() + " while getting versions from JIRA service.", e);
            throw new JiraException(e);
        }
        return null;
    }

    /**
     * Marks specified {@code version} as released.
     * 
     * @param version
     *            Version that should be marked as released.
     * @throws JiraException
     */
    public void releaseVersion(RemoteVersion version) throws JiraException {
        version.setReleased(true);
        version.setReleaseDate(Calendar.getInstance());
        try {
            soapService.releaseVersion(soapToken, projectKey, version);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getClass().getName() + " while releasing version in JIRA.", e);
            throw new JiraException(e);
        }
    }
}
