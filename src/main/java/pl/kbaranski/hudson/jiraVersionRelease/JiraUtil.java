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

    /**
     * Logg in to JIRA SOAP service.
     * 
     * @throws ServiceException
     * @throws RemoteAuthenticationException
     * @throws RemoteException
     * @throws java.rmi.RemoteException
     */
    public void connect() throws ServiceException, RemoteAuthenticationException, RemoteException,
            java.rmi.RemoteException {
        JiraSoapServiceService serviceLocator = new JiraSoapServiceServiceLocator();
        // Podłączenie do JIRA + logowanie
        soapService = serviceLocator.getJirasoapserviceV2(trackerInstance.getUrl());
        soapToken = soapService.login(trackerInstance.getUser(), trackerInstance.getPass());
    }

    /**
     * Creates version in JIRA tracker with name specified in {@code fullName}
     * parameter.
     * 
     * @param fullName
     *            Name of version to create in JIRA.
     * @throws RemoteException
     * @throws java.rmi.RemoteException
     */
    public void createVersion(String fullName) throws RemoteException, java.rmi.RemoteException {
        // tworzymy nową wersję
        RemoteVersion newVer = new RemoteVersion();
        newVer.setName(fullName);
        soapService.addVersion(soapToken, projectKey, newVer);
    }

    /**
     * Logouts from JIRA.
     * 
     * @throws java.rmi.RemoteException
     */
    public void disconnect() throws java.rmi.RemoteException {
        soapService.logout(soapToken);
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
     * @throws RemotePermissionException
     * @throws RemoteAuthenticationException
     * @throws RemoteException
     * @throws java.rmi.RemoteException
     */
    public RemoteVersion getVersion(int buildNumber) throws RemotePermissionException, RemoteAuthenticationException,
            RemoteException, java.rmi.RemoteException {
        Pattern versionNumberPrefixPattern = Pattern.compile(prefixRegexp);
        String versionNumberFullRegex = prefixRegexp + buildNumber;

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
        return null;
    }

    /**
     * Marks specified {@code version} as released.
     * 
     * @param version
     *            Version that should be marked as released.
     * @throws RemoteException
     * @throws java.rmi.RemoteException
     */
    public void releaseVersion(RemoteVersion version) throws RemoteException, java.rmi.RemoteException {
        version.setReleased(true);
        version.setReleaseDate(Calendar.getInstance());
        soapService.releaseVersion(soapToken, projectKey, version);
    }
}
