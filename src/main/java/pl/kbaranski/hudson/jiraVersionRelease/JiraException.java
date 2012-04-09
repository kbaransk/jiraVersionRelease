package pl.kbaranski.hudson.jiraVersionRelease;

public class JiraException extends Exception {
    private static final long serialVersionUID = 1L;

    public JiraException() {
        super();
    }

    public JiraException(Throwable cause) {
        super(cause);
    }

    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }
}
