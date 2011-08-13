# JIRA Version Releaser

## Purpose
After a successful build, Hudson should automatically mark the current version of the project as released in JIRA and create the next one (without releasing).
As a result QA team can file a bug to specified build without playing with JIRA administration section.

## Known bugs / limitations
* Description of a version cannot be set to anything other than it's name. Remote API of JIRA doesn't provide any method to achieve that.

## ToDo
* prcechowywanie ID RemoteVersion, wyświetlanie linka do wersji w JENKINSIE.
* bugi zamykane do wersji widoczne w Jenkinsie (tak, jak Changes do rewizji na jenkins/job/JOB_NAME/BUILD_NUMBER)

