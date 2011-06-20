package pl.kbaranski.hudson.jiraVersionRelease;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.rpc.ServiceException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceService;
import pl.kbaranski.hudson.jiraVersionRelease.soap.JiraSoapServiceServiceLocator;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteAuthenticationException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteException;
import pl.kbaranski.hudson.jiraVersionRelease.soap.RemoteVersion;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link JiraVersionReleasePublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JiraVersionReleasePublisher extends Notifier {

    private String url;
    private String user;
    private String pass;
    
    private String projectKey;
    
    private String prefixRegexp;
    
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	@DataBoundConstructor
	public JiraVersionReleasePublisher(String url, String user, String pass, String projectKey, String prefixRegexp) {
		this.url = url;
		this.user = user;
		this.pass = pass;
		this.projectKey = projectKey;
		this.prefixRegexp = prefixRegexp;
	}
	
 	public String getUrl() {
		return url;
	}

	public String getUser() {
		return user;
	}

	public String getPass() {
		return pass;
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    	JiraSoapServiceService serviceLocator = new JiraSoapServiceServiceLocator();

    	try {
    		//	Wzorce itp wykorzystywane przy sprawdzaniu wersji
			Pattern versionNumberPrefixPattern = Pattern.compile(prefixRegexp);
			String versionNumberFullRegex = prefixRegexp + build.getNumber();
			String newVerName = null;			

    		//	Podłączenie do JIRA + logowanie
			JiraSoapService soapService = serviceLocator.getJirasoapserviceV2(new URL(this.url));
			String soapToken = soapService.login(this.user, this.pass);
			
			//	Pobranie zdalnych wersji i ich sprawdzanie
			RemoteVersion[] versions = soapService.getVersions(soapToken, this.projectKey);
			for (RemoteVersion version : versions) {
				//	Z wersjami wydanymi nic nie chcemy robic
				if (version.isReleased() || version.isArchived()) {
					continue;
				}
				//	Pomijamy również wersje nie pasujące do wzorca
				if (!Pattern.matches(versionNumberFullRegex, version.getName())) {
//					System.out.println(version.getName() + " nie pasuje do wzorca: " + versionNumberFullRegex);
					continue;
				}
				//	Teraz wiemy już, że znaleziona wersja, to ta odpowiadająca poprzednio zbudowanej wersji w Hudson
				Matcher matcher = versionNumberPrefixPattern.matcher(version.getName());
				if (matcher.find()) {
					newVerName = matcher.group();
					version.setReleased(true);
					version.setReleaseDate(Calendar.getInstance());
					soapService.releaseVersion(soapToken, projectKey, version);
					listener.getLogger().println("JIRA: Wydano wersje " + version.getName() + " w projekcie " + this.getProjectKey());
					break;
				}
			}
			
			if (newVerName != null) {
				// tworzymy nową wersję
				String newVersionFullName = newVerName + (build.getNumber() + 1);
				RemoteVersion newVer = new RemoteVersion();
				newVer.setName(newVersionFullName);
				soapService.addVersion(soapToken, projectKey, newVer);
				listener.getLogger().println("JIRA: Utworzono wersje " + newVersionFullName + " w projekcie " + this.getProjectKey());
				return true;
			}
			else {
				listener.getLogger().println("JIRA: W JIRA nie odnaleziono wersji odpowiadającej biezacemu numerowi kopilacji");
			}
			soapService.logout(soapToken);
		} catch (MalformedURLException e) {
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		} catch (ServiceException e) {
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		} catch (RemoteAuthenticationException e) {
			listener.getLogger().println("JIRA: Nie uwierzytelniono uzytkownika");
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		} catch (RemoteException e) {
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		} catch (java.rmi.RemoteException e) {
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		} catch (Exception e) {
			System.out.println(new StringBuilder().append("[").append(e.getClass().getSimpleName()).append("] ").append(e.getMessage()).append(": \n").append(e.getStackTrace()).toString());
		}
        return false;
    }
    
    /**
     * Descriptor for {@link JiraVersionReleasePublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/JiraVersionReleasePublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(JiraVersionReleasePublisher.class);
        }

        @Override
        public String getDisplayName() {
            return "JIRA Version Releaser";
        }
        
        @Override
        public String getHelpFile() {
        	return "/plugin/jiraVersionRelease/help-config.html";
        }

		@SuppressWarnings("unchecked")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> arg0) {
			return true;
		}
    }
}

