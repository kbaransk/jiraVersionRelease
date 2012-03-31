package pl.kbaranski.hudson.jiraVersionRelease;

import java.util.Calendar;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.rpc.ServiceException;

import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceServiceLocator;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteAuthenticationException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemotePermissionException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteVersion;

public class JiraUtil {
    private final static Logger LOG = Logger
            .getLogger(JiraUtil.class.getName());

    private String jiraVersionNamePrefix;
    private String prefixRegexp;
    private String projectKey;
    private JiraSoapServiceService serviceLocator;
    private JiraSoapService soapService;
    private String soapToken;
    private TrackerInstance trackerInstance;

    public JiraUtil(TrackerInstance trackerInstance, String projectKey,
            String prefixRegexp) {
        this.trackerInstance = trackerInstance;
        this.projectKey = projectKey;
        this.prefixRegexp = prefixRegexp;
    }

    public void connect() throws ServiceException,
            RemoteAuthenticationException, RemoteException,
            java.rmi.RemoteException {
        serviceLocator = new JiraSoapServiceServiceLocator();
        // Podłączenie do JIRA + logowanie
        soapService = serviceLocator.getJirasoapserviceV2(trackerInstance
                .getUrl());
        soapToken = soapService.login(trackerInstance.getUser(),
                trackerInstance.getPass());
    }

    public void createVersion(String fullName) throws RemoteException,
            java.rmi.RemoteException {
        // tworzymy nową wersję
        RemoteVersion newVer = new RemoteVersion();
        newVer.setName(fullName);
        soapService.addVersion(soapToken, projectKey, newVer);
    }

    public void disconnect() throws java.rmi.RemoteException {
        soapService.logout(soapToken);
    }

    public String getJiraVersionNamePrefix() {
        return jiraVersionNamePrefix;
    }

    private RemoteVersion[] getRemoteVersions()
            throws RemotePermissionException, RemoteAuthenticationException,
            RemoteException, java.rmi.RemoteException {
        return soapService.getVersions(soapToken, this.projectKey);
    }

    public RemoteVersion getVersion(int buildNumber)
            throws RemotePermissionException, RemoteAuthenticationException,
            RemoteException, java.rmi.RemoteException {
        Pattern versionNumberPrefixPattern = Pattern.compile(prefixRegexp);
        String versionNumberFullRegex = prefixRegexp + buildNumber;

        for (RemoteVersion version : getRemoteVersions()) {
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
            Matcher matcher = versionNumberPrefixPattern.matcher(version
                    .getName());
            if (matcher.find()) {
                jiraVersionNamePrefix = matcher.group();
                return version;
            }
        }
        return null;
    }

    public void releaseVersion(RemoteVersion version) throws RemoteException,
            java.rmi.RemoteException {
        version.setReleased(true);
        version.setReleaseDate(Calendar.getInstance());
        soapService.releaseVersion(soapToken, projectKey, version);
    }
}
