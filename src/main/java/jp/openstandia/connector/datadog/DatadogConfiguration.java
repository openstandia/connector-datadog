/*
 *  Copyright Nomura Research Institute, Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.openstandia.connector.datadog;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Connector Configuration implementation for datadog connector.
 *
 * @author Hiroyuki Wada
 */
public class DatadogConfiguration extends AbstractConfiguration {

    private GuardedString apiKey;
    private GuardedString appKey;
    private String datadogSite;

    private String httpProxyHost;
    private int httpProxyPort;
    private String httpProxyUser;
    private GuardedString httpProxyPassword;
    private int queryPageSize = 50;
    private int connectionTimeoutInMilliseconds = 10000; // 10s
    private int readTimeoutInMilliseconds = 30000; // 30s

    @ConfigurationProperty(
            order = 1,
            displayMessageKey = "API Key",
            helpMessageKey = "Set API Key for Datadog.",
            required = true,
            confidential = true)
    public GuardedString getApiKey() {
        return apiKey;
    }

    public void setApiKey(GuardedString apiKey) {
        this.apiKey = apiKey;
    }

    @ConfigurationProperty(
            order = 2,
            displayMessageKey = "Application Key",
            helpMessageKey = "Set Application Key for Datadog",
            required = true,
            confidential = true)
    public GuardedString getAppKey() {
        return appKey;
    }

    public void setAppKey(GuardedString appKey) {
        this.appKey = appKey;
    }

    @ConfigurationProperty(
            order = 3,
            displayMessageKey = "Datadog Site",
            helpMessageKey = "Set datadog site if you want change it Datadog EU site etc. (e.g. https://api.datadoghq.eu)",
            required = false,
            confidential = false)
    public String getDatadogSite() {
        return datadogSite;
    }

    public void setDatadogSite(String datadogSite) {
        this.datadogSite = datadogSite;
    }

    @ConfigurationProperty(
            order = 4,
            displayMessageKey = "Query page size",
            helpMessageKey = "Set page size for datadog list API (Default: 50)",
            required = false,
            confidential = false)
    public int getQueryPageSize() {
        return queryPageSize;
    }

    public void setQueryPageSize(int queryPageSize) {
        this.queryPageSize = queryPageSize;
    }


    @ConfigurationProperty(
            order = 5,
            displayMessageKey = "Connection Timeout",
            helpMessageKey = "Connection timeout in milliseconds. (Default: 10000)",
            required = false,
            confidential = false)
    public int getConnectionTimeoutInMilliseconds() {
        return connectionTimeoutInMilliseconds;
    }

    public void setConnectionTimeoutInMilliseconds(int connectionTimeoutInMilliseconds) {
        this.connectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 6,
            displayMessageKey = "Read Timeout",
            helpMessageKey = "Read timeout in milliseconds. (Default: 30000)",
            required = false,
            confidential = false)
    public int getReadTimeoutInMilliseconds() {
        return readTimeoutInMilliseconds;
    }

    public void setReadTimeoutInMilliseconds(int readTimeoutInMilliseconds) {
        this.readTimeoutInMilliseconds = readTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 7,
            displayMessageKey = "HTTP Proxy Host",
            helpMessageKey = "Hostname for the HTTP Proxy",
            required = false,
            confidential = false)
    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    @ConfigurationProperty(
            order = 8,
            displayMessageKey = "HTTP Proxy Port",
            helpMessageKey = "Port for the HTTP Proxy",
            required = false,
            confidential = false)
    public int getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(int httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    @ConfigurationProperty(
            order = 9,
            displayMessageKey = "HTTP Proxy User",
            helpMessageKey = "Username for the HTTP Proxy Authentication",
            required = false,
            confidential = false)
    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    @ConfigurationProperty(
            order = 10,
            displayMessageKey = "HTTP Proxy Password",
            helpMessageKey = "Password for the HTTP Proxy Authentication",
            required = false,
            confidential = true)
    public GuardedString getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(GuardedString httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    @Override
    public void validate() {
    }
}
