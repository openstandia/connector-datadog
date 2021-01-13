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
package jp.openstandia.connector.datadog.rest;

import com.datadog.api.v2.client.ApiClient;
import com.datadog.api.v2.client.ApiException;
import com.datadog.api.v2.client.ApiResponse;
import com.datadog.api.v2.client.api.RolesApi;
import com.datadog.api.v2.client.api.UsersApi;
import com.datadog.api.v2.client.model.*;
import jp.openstandia.connector.datadog.DatadogClient;
import jp.openstandia.connector.datadog.DatadogConfiguration;
import jp.openstandia.connector.datadog.DatadogSchema;
import jp.openstandia.connector.datadog.DatadogUtils;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static jp.openstandia.connector.datadog.DatadogUserHandler.*;
import static jp.openstandia.connector.datadog.DatadogUtils.*;
import static org.identityconnectors.framework.common.objects.OperationalAttributes.ENABLE_NAME;

/**
 * Datadog client implementation which uses Datadog REST client.
 *
 * @author Hiroyuki Wada
 */
public class DatadogRESTClient implements DatadogClient {

    private static final Log LOGGER = Log.getLog(DatadogRESTClient.class);

    private final String instanceName;
    private final DatadogConfiguration cofiguration;
    private final ApiClient apiClient;

    public DatadogRESTClient(String instanceName, DatadogConfiguration configuration) {
        this.instanceName = instanceName;
        this.cofiguration = configuration;

        ApiClient apiClient = new ApiClient() {
            @Override
            protected void performAdditionalClientConfiguration(ClientConfig clientConfig) {
                // HTTP proxy configuration
                if (StringUtil.isNotEmpty(configuration.getHttpProxyHost()) &&
                        configuration.getHttpProxyPort() > 0) {

                    if (StringUtil.isNotEmpty(configuration.getHttpProxyUser())
                            && configuration.getHttpProxyPassword() != null) {
                        // When using apacheConnectorProvider, it might cause memory leak...
                        // https://github.com/eclipse-ee4j/jersey/pull/3861

                        // However, we need to use it to support proxy authentication
                        clientConfig.connectorProvider(new ApacheConnectorProvider());
                        clientConfig.property(ClientProperties.PROXY_URI, String.format("http://%s:%d",
                                configuration.getHttpProxyHost(),
                                configuration.getHttpProxyPort()));

                        clientConfig.property(ClientProperties.PROXY_USERNAME, configuration.getHttpProxyUser());
                        configuration.getHttpProxyPassword().access(c -> {
                            clientConfig.property(ClientProperties.PROXY_PASSWORD, String.valueOf(c));
                        });

                    } else {
                        HttpUrlConnectorProvider.ConnectionFactory factory = new HttpUrlConnectorProvider.ConnectionFactory() {
                            @Override
                            public HttpURLConnection getConnection(URL url) throws IOException {
                                return (HttpURLConnection) url
                                        .openConnection(new Proxy(Proxy.Type.HTTP,
                                                new InetSocketAddress(configuration.getHttpProxyHost(),
                                                        configuration.getHttpProxyPort())));
                            }
                        };
                        clientConfig.connectorProvider(new HttpUrlConnectorProvider().connectionFactory(factory));
                    }
                }
            }
        };

        // Configure the Datadog site to send API calls to
        if (StringUtil.isNotEmpty(configuration.getDatadogSite())) {
            Map<String, String> serverVariables = new HashMap<>();
            serverVariables.put("site", configuration.getDatadogSite());

            apiClient.setServerVariables(serverVariables);
        }

        // Configure API key authorization:
        final HashMap<String, String> secrets = new HashMap<>();
        configuration.getApiKey().access(c -> {
            secrets.put("apiKeyAuth", String.valueOf(c));
        });
        configuration.getAppKey().access(c -> {
            secrets.put("appKeyAuth", String.valueOf(c));
        });
        apiClient.configureApiKeys(secrets);

        // Configure timeout

        apiClient.setConnectTimeout(configuration.getConnectionTimeoutInMilliseconds());

        apiClient.setReadTimeout(configuration.getReadTimeoutInMilliseconds());

        // Keep the client instance per connector pool.
        // https://github.com/DataDog/datadog-api-client-java says
        // "It's recommended to create an instance of ApiClient per thread in a multithreaded environment to avoid any potential issues."
        this.apiClient = apiClient;
    }

    @Override
    public void test() {
        try {
            UsersApi apiInstance = new UsersApi(apiClient);
            ApiResponse<UsersResponse> res = apiInstance.listUsers().executeWithHttpInfo();
            if (res.getStatusCode() != 200) {
                throw new ConnectorException("This datadog connector isn't active.");
            }
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    protected ConnectorException handleApiException(ApiException e) {
        LOGGER.error("Exception when calling datadog api. Status code: {0}, Reason: {1}, Response headers: {2} ",
                e.getCode(), e.getResponseBody(), e.getResponseHeaders());

        if (e.getCode() == 400) {
            return new InvalidAttributeValueException(e);
        }
        if (e.getCode() == 403) {
            return new ConnectionFailedException(e);
        }
        if (e.getCode() == 404) {
            return new UnknownUidException(e);
        }
        if (e.getCode() == 409) {
            return new AlreadyExistsException(e);
        }

        return new ConnectorIOException("Failed to call datadog api", e);
    }

    /**
     * Fetch all roles with reverse map.
     *
     * @return map with key: roleName(lowerCase), value: roleId
     */
    protected Map<String, String> getReverseAllRoleMap() {
        RolesApi apiInstance = new RolesApi(apiClient);

        try {
            // If we need to support custom role which is for opt-in enterprise feature in datadog,
            // we need to implement paging.
            // Without custom role, the count of default roles is only 3.
            RolesResponse res = apiInstance
                    .listRoles()
                    .pageNumber(0L)
                    .pageSize(100L)
                    .execute();

            // Generate map with key: roleName(lowerCase), value: roleId
            return res.getData().stream()
                    .collect(Collectors.toMap(
                            r -> r.getAttributes().getName().toLowerCase(),
                            r -> r.getId()
                    ));
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    /**
     * Fetch all roles.
     *
     * @return map with key: roleId, value: roleName(lowerCase)
     */
    protected Map<String, String> getAllRoleMap() {
        RolesApi apiInstance = new RolesApi(apiClient);

        try {
            // If we need to support custom role which is for opt-in enterprise feature in datadog,
            // we need to implement paging.
            // Without custom role, the count of default roles is only 3.
            RolesResponse res = apiInstance
                    .listRoles()
                    .pageNumber(0L)
                    .pageSize(100L)
                    .execute();

            // Generate map with key: roleId, roleName
            return res.getData().stream()
                    .collect(Collectors.toMap(
                            r -> r.getId(),
                            r -> r.getAttributes().getName()
                    ));
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    @Override
    public Uid createUser(DatadogSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException {
        UsersApi apiInstance = new UsersApi(apiClient);

        UserCreateAttributes attrs = new UserCreateAttributes();
        RelationshipToRoles roles = new RelationshipToRoles();

        List<Object> roleNames = null;
        boolean doInvitation = false;
        boolean doDisable = false;

        for (Attribute attr : createAttributes) {
            if (attr.is(Name.NAME)) {
                attrs.setEmail(AttributeUtil.getStringValue(attr));

            } else if (attr.is(ENABLE_NAME)) {
                doDisable = !AttributeUtil.getBooleanValue(attr);

            } else if (attr.is(ATTR_NAME)) {
                attrs.setName(AttributeUtil.getStringValue(attr));

            } else if (attr.is(ATTR_TITLE)) {
                attrs.setTitle(AttributeUtil.getStringValue(attr));

            } else if (attr.is(ATTR_INVITATION)) {
                Boolean b = AttributeUtil.getBooleanValue(attr);
                if (b != null && b.booleanValue()) {
                    doInvitation = true;
                }

            } else if (attr.is(ATTR_ROLE_NAMES)) {
                roleNames = attr.getValue();
            }
        }

        UserCreateData userData = new UserCreateData();
        userData.setType(UsersType.USERS);
        userData.setAttributes(attrs);

        if (roleNames != null && !roleNames.isEmpty()) {
            Map<String, String> allRoles = getReverseAllRoleMap();

            for (Object v : roleNames) {
                String roleName = v.toString().toLowerCase();
                if (!allRoles.containsKey(roleName)) {
                    throw new InvalidAttributeValueException("Invalid datadog role name: " + v);
                }

                RelationshipToRoleData roleData = new RelationshipToRoleData()
                        .type(RolesType.ROLES)
                        .id(allRoles.get(roleName));
                roles.addDataItem(roleData);
            }
            UserRelationships relationships = new UserRelationships();
            relationships.setRoles(roles);

            userData.setRelationships(relationships);
        }

        UserCreateRequest newUser = new UserCreateRequest();
        newUser.setData(userData);

        try {
            LOGGER.ok("[{0}] Create datadog user: {1}", instanceName, userData);

            ApiResponse<UserResponse> res = apiInstance.createUser().body(newUser).executeWithHttpInfo();
            if (res.getStatusCode() != 201) {
                throw new ConnectorIOException("Invalid status code when creating datadog user. status: " + res.getStatusCode());
            }

            // After created, we need to disable the user if requested
            if (doDisable) {
                disable(res.getData().getData().getId());
            }

            // After created, we need to invite the user if requested
            if (!doDisable && doInvitation) {
                invite(res.getData().getData().getId());
            }

            return new Uid(res.getData().getData().getId(), new Name(res.getData().getData().getAttributes().getHandle()));
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    protected void disable(String userId) {
        UsersApi apiInstance = new UsersApi(apiClient);

        UserUpdateAttributes attrs = new UserUpdateAttributes();
        attrs.setDisabled(true);

        UserUpdateData userData = new UserUpdateData();
        userData.setType(UsersType.USERS);
        userData.setId(userId); // required
        userData.setAttributes(attrs);

        UserUpdateRequest user = new UserUpdateRequest();
        user.setData(userData);

        try {
            LOGGER.ok("[{0}] Disable datadog user: {1}", instanceName, userId);

            apiInstance.updateUser(userId).body(user).executeWithHttpInfo();
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    protected void invite(String userId) {
        UsersApi apiInstance = new UsersApi(apiClient);

        RelationshipToUserData userData = new RelationshipToUserData();
        userData.setId(userId);

        RelationshipToUser relationshipToUser = new RelationshipToUser();
        relationshipToUser.data(userData);

        UserInvitationRelationships relationships = new UserInvitationRelationships();
        relationships.user(relationshipToUser);

        UserInvitationData data = new UserInvitationData();
        data.setType(UserInvitationsType.USER_INVITATIONS);
        data.setRelationships(relationships);

        UserInvitationsRequest req = new UserInvitationsRequest();
        req.addDataItem(data);

        try {
            LOGGER.ok("[{0}] Invite datadog user: {1}", instanceName, userId);

            apiInstance.sendInvitations().body(req).execute();
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    @Override
    public void updateUser(DatadogSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException {
        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            UserUpdateAttributes datadogAttrs = new UserUpdateAttributes();

            Map<String, String> allRoles = null;
            List<String> assignRoleIds = new ArrayList<>();
            List<String> unassignRoleIds = new ArrayList<>();

            boolean doInvitation = false;
            boolean doUpdateAttrs = false;

            for (AttributeDelta delta : modifications) {
                if (delta.is(Name.NAME)) {
                    // Can't modify handle attribute

                }
                if (delta.is(ENABLE_NAME)) {
                    datadogAttrs.setDisabled(!AttributeDeltaUtil.getBooleanValue(delta));
                    doUpdateAttrs = true;

                } else if (delta.is(ATTR_EMAIL)) {
                    String value = AttributeDeltaUtil.getStringValue(delta);
                    if (value == null) {
                        throw new InvalidAttributeValueException("Invalid datadog email. It cannot be deleted.");
                    }
                    datadogAttrs.setEmail(value);
                    doUpdateAttrs = true;

                } else if (delta.is(ATTR_NAME)) {
                    datadogAttrs.setName(toResourceAttributeValue(AttributeDeltaUtil.getStringValue(delta)));
                    doUpdateAttrs = true;

                } else if (delta.is(ATTR_INVITATION)) {
                    doInvitation = AttributeDeltaUtil.getBooleanValue(delta);

                } else if (delta.is(ATTR_ROLE_NAMES)) {
                    allRoles = getReverseAllRoleMap();

                    List<Object> valuesToAdd = delta.getValuesToAdd();
                    if (valuesToAdd != null) {
                        for (Object o : valuesToAdd) {
                            String roleId = allRoles.get(o.toString().toLowerCase());
                            if (roleId == null) {
                                throw new InvalidAttributeValueException("Invalid datadog role name: " + o);
                            }
                            assignRoleIds.add(roleId);
                        }
                    }

                    List<Object> valuesToRemove = delta.getValuesToRemove();
                    if (valuesToRemove != null) {
                        for (Object o : valuesToRemove) {
                            String roleId = allRoles.get(o.toString().toLowerCase());
                            if (roleId == null) {
                                throw new InvalidAttributeValueException("Invalid datadog role name: " + o);
                            }
                            unassignRoleIds.add(roleId);
                        }
                    }
                }
            }

            // Update user attributes if needed
            if (doUpdateAttrs) {

                UserUpdateData userData = new UserUpdateData();
                userData.setType(UsersType.USERS);
                userData.setId(uid.getUidValue()); // required
                userData.setAttributes(datadogAttrs);

                UserUpdateRequest user = new UserUpdateRequest();
                user.setData(userData);

                LOGGER.ok("[{0}] Update datadog user: {1}", instanceName, user);

                apiInstance.updateUser(uid.getUidValue()).body(user).executeWithHttpInfo();
            }

            // Update role association if needed
            if (allRoles != null) {
                assignRole(allRoles, uid.getUidValue(), assignRoleIds);
                unassignRole(allRoles, uid.getUidValue(), unassignRoleIds);
            }

            // Send invitation if needed
            if (doInvitation && isPendingUser(uid.getUidValue())) {
                invite(uid.getUidValue());
            }

        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    protected boolean isPendingUser(String userId) {
        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            return apiInstance.getUser(userId)
                    .execute()
                    .getData()
                    .getAttributes()
                    .getStatus()
                    .equals("Pending");
        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }


    protected void assignRole(Map<String, String> allRoles, String userId, List<String> roleIds) throws ApiException {
        for (String roleId : roleIds) {
            RolesApi apiInstance = new RolesApi(apiClient);

            RelationshipToUserData relationshipToUserData = new RelationshipToUserData();
            relationshipToUserData.setId(userId);

            RelationshipToUser relationshipToUser = new RelationshipToUser();
            relationshipToUser.setData(relationshipToUserData);

            LOGGER.ok("[{0}] Assign datadog role: {1}", instanceName, relationshipToUser);

            apiInstance.addUserToRole(roleId).body(relationshipToUser).execute();
        }
    }

    protected void unassignRole(Map<String, String> allRoles, String userId, List<String> roleIds) throws ApiException {
        for (String roleId : roleIds) {
            RolesApi apiInstance = new RolesApi(apiClient);

            RelationshipToUserData relationshipToUserData = new RelationshipToUserData();
            relationshipToUserData.setId(userId);

            RelationshipToUser relationshipToUser = new RelationshipToUser();
            relationshipToUser.setData(relationshipToUserData);

            LOGGER.ok("[{0}] Unassign datadog role: {1}", instanceName, relationshipToUser);

            apiInstance.removeUserFromRole(roleId).body(relationshipToUser).execute();
        }
    }

    @Override
    public void deleteUser(DatadogSchema schema, Uid uid, OperationOptions options) throws UnknownUidException {
        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            LOGGER.ok("[{0}] Delete(disable) datadog user: {1}", instanceName, uid.getUidValue());

            // Datadog doesn't have delete user API
            ApiResponse<Void> res = apiInstance.disableUser(uid.getUidValue()).executeWithHttpInfo();
            if (res.getStatusCode() != 204) {
                throw new ConnectorIOException("Invalid status code when disabling datadog user. status: " + res.getStatusCode());
            }

        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    @Override
    public void getUsers(DatadogSchema schema, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize) {
        boolean allowPartialAttributeValues = shouldAllowPartialAttributeValues(options);

        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            UsersApi.APIlistUsersRequest req = apiInstance.listUsers()
                    .pageSize(queryPageSize)
                    .sort("email");

            long start = 0;

            while (true) {
                UsersResponse res = req.pageNumber(start).execute();

                List<User> results = res.getData();

                if (results.size() == 0) {
                    break;
                }

                for (User u : results) {
                    handler.handle(toConnectorObject(schema, u, attributesToGet, allowPartialAttributeValues, queryPageSize));
                }

                start++;
            }

        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    @Override
    public void getUser(DatadogSchema schema, Uid uid, ResultsHandler handler, OperationOptions options,
                        Set<String> attributesToGet, long queryPageSize) {
        boolean allowPartialAttributeValues = shouldAllowPartialAttributeValues(options);

        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            UserResponse res = apiInstance.getUser(uid.getUidValue()).execute();
            User user = res.getData();

            handler.handle(toConnectorObject(schema, user, attributesToGet, allowPartialAttributeValues, -1));

        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    @Override
    public void getUser(DatadogSchema schema, Name name, ResultsHandler handler, OperationOptions options,
                        Set<String> attributesToGet, long queryPageSize) {
        boolean allowPartialAttributeValues = shouldAllowPartialAttributeValues(options);

        UsersApi apiInstance = new UsersApi(apiClient);

        try {
            UsersApi.APIlistUsersRequest req = apiInstance.listUsers()
                    .pageSize(queryPageSize)
                    .sort("email");

            long start = 0;

            while (true) {
                UsersResponse res = req.pageNumber(start).execute();

                List<User> results = res.getData();

                if (results.size() == 0) {
                    break;
                }

                for (User u : results) {
                    if (u.getAttributes().getHandle().equalsIgnoreCase(name.getNameValue())) {
                        // Found
                        handler.handle(toConnectorObject(schema, u, attributesToGet, allowPartialAttributeValues, queryPageSize));
                        return;
                    }
                }

                start++;
            }

        } catch (ApiException e) {
            throw handleApiException(e);
        }
    }

    private ConnectorObject toConnectorObject(DatadogSchema schema, User user,
                                              Set<String> attributesToGet, boolean allowPartialAttributeValues, long queryPageSize) {
        final ConnectorObjectBuilder builder = new ConnectorObjectBuilder()
                .setObjectClass(USER_OBJECT_CLASS)
                // Always returns "id"
                .setUid(user.getId())
                // Always returns "handle"
                .setName(user.getAttributes().getHandle());

        // Attributes
        if (shouldReturn(attributesToGet, ATTR_EMAIL)) {
            builder.addAttribute(ATTR_EMAIL, user.getAttributes().getEmail());
        }
        if (shouldReturn(attributesToGet, ATTR_NAME)) {
            builder.addAttribute(ATTR_NAME, user.getAttributes().getName());
        }
        if (shouldReturn(attributesToGet, ATTR_TITLE)) {
            builder.addAttribute(ATTR_TITLE, user.getAttributes().getTitle());
        }
        if (shouldReturn(attributesToGet, ATTR_ICON)) {
            builder.addAttribute(ATTR_ICON, user.getAttributes().getIcon());
        }

        // Metadata
        if (shouldReturn(attributesToGet, ENABLE_NAME)) {
            builder.addAttribute(AttributeBuilder.buildEnabled(!user.getAttributes().getDisabled()));
        }
        if (shouldReturn(attributesToGet, ATTR_CREATED_AT)) {
            builder.addAttribute(ATTR_CREATED_AT, DatadogUtils.toZoneDateTime(user.getAttributes().getCreatedAt()));
        }
        if (shouldReturn(attributesToGet, ATTR_VERIFIED)) {
            builder.addAttribute(ATTR_VERIFIED, user.getAttributes().getVerified());
        }
        if (shouldReturn(attributesToGet, ATTR_STATUS)) {
            builder.addAttribute(ATTR_STATUS, user.getAttributes().getStatus());
        }

        if (allowPartialAttributeValues) {
            // Suppress fetching roleNames
            LOGGER.ok("[{0}] Suppress fetching roleNames because return partial attribute values is requested", instanceName);

            AttributeBuilder ab = new AttributeBuilder();
            ab.setName(ATTR_ROLE_NAMES).setAttributeValueCompleteness(AttributeValueCompleteness.INCOMPLETE);
            ab.addValue(Collections.EMPTY_LIST);
            builder.addAttribute(ab.build());

        } else {
            if (attributesToGet == null) {
                // Suppress fetching roleNames default
                LOGGER.ok("[{0}] Suppress fetching roleNames because returned by default is true", instanceName);

            } else if (shouldReturn(attributesToGet, ATTR_ROLE_NAMES)) {
                // Fetch roleNames
                LOGGER.ok("[{0}] Fetching roleNames because attributes to get is requested", instanceName);

                Map<String, String> allRoles = getAllRoleMap();

                List<RelationshipToRoleData> data = user.getRelationships().getRoles().getData();
                List<String> roleNames = data.stream()
                        .filter(r -> allRoles.containsKey(r.getId()))
                        .map(r -> allRoles.get(r.getId()))
                        .collect(Collectors.toList());

                builder.addAttribute(ATTR_ROLE_NAMES, roleNames);
            }
        }

        return builder.build();
    }

    @Override
    public void close() {
    }
}
