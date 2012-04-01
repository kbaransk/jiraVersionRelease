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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
