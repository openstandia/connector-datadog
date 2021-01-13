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

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.time.ZonedDateTime;
import java.util.Set;

import static jp.openstandia.connector.datadog.DatadogUtils.createFullAttributesToGet;

/**
 * Handle datadog user object.
 *
 * @author Hiroyuki Wada
 */
public class DatadogUserHandler extends AbstractDatadogHandler {

    public static final ObjectClass USER_OBJECT_CLASS = new ObjectClass("user");

    private static final Log LOGGER = Log.getLog(DatadogUserHandler.class);

    // Unique and unchangeable.
    // Don't use "id" here because it conflicts midpoint side.
    private static final String ATTR_USER_ID = "userId";

    // Unique and unchangeable.
    private static final String ATTR_HANDLE = "handle";

    // Attributes
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_TITLE = "title";

    // Readonly
    public static final String ATTR_ICON = "icon";
    public static final String ATTR_CREATED_AT = "createdAt";
    public static final String ATTR_VERIFIED = "verified";
    public static final String ATTR_STATUS = "status"; // Pending, Active, Disabled

    // Role
    public static final String ATTR_ROLE_NAMES = "roleNames"; // Datadog Admin Role, Datadog Read Only Role, Datadog Standard Role

    // Invitation
    public static final String ATTR_INVITATION = "invitation";

    // Association
    // Not implemented yet because custom role is opt-in enterprise feature
    public static final String ATTR_ROLES = "roles"; // roleId(UUID)

    public DatadogUserHandler(String instanceName, DatadogConfiguration configuration, DatadogClient client,
                              DatadogSchema schema) {
        super(instanceName, configuration, client, schema);
    }

    public static ObjectClassInfo getUserSchema() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(USER_OBJECT_CLASS.getObjectClassValue());

        // id (__UID__)
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Uid.NAME)
                        .setRequired(false) // Must be optional. It is not present for create operations
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .setNativeName(ATTR_USER_ID)
                        .build());

        // email (__NAME__)
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Name.NAME)
                        .setRequired(true)
                        .setUpdateable(false)
                        .setNativeName(ATTR_HANDLE)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .build());

        // __ENABLE__ attribute
        builder.addAttributeInfo(OperationalAttributeInfos.ENABLE);

        // __PASSWORD__ attribute
        // Datadog API doesn't support password
        // builder.addAttributeInfo(OperationalAttributeInfos.PASSWORD);

        // attributes
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_EMAIL)
                        .setRequired(false)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_NAME)
                        .setRequired(false)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_TITLE)
                        .setRequired(false)
                        .setUpdateable(false)
                        .build());

        // Readonly
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_ICON)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_CREATED_AT)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setType(ZonedDateTime.class)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_VERIFIED)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setType(Boolean.class)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_STATUS)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .build());

        // Role
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_ROLE_NAMES)
                        .setRequired(false)
                        .setMultiValued(true)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .build());

        // Invitation
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_INVITATION)
                        .setRequired(false)
                        .setType(Boolean.class)
                        .build());

        // Association
        // Not implemented yet because custom role is opt-in enterprise feature
//        builder.addAttributeInfo(
//                AttributeInfoBuilder.define(ATTR_ROLES)
//                        .setRequired(false)
//                        .setMultiValued(true)
//                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
//                        .build());

        ObjectClassInfo userSchemaInfo = builder.build();

        LOGGER.ok("The constructed datadog user schema: {0}", userSchemaInfo);

        return userSchemaInfo;
    }

    /**
     * @param attributes
     * @return
     */
    @Override
    public Uid create(Set<Attribute> attributes) {
        return client.createUser(schema, attributes);
    }

    /**
     * @param uid
     * @param modifications
     * @param options
     * @return
     */
    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        client.updateUser(schema, uid, modifications, options);

        return null;
    }

    /**
     * @param uid
     * @param options
     */
    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteUser(schema, uid, options);
    }


    @Override
    public void query(DatadogFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        // Create full attributesToGet by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET
        Set<String> attributesToGet = createFullAttributesToGet(schema.userSchema, options);

        if (filter == null) {
            client.getUsers(schema,
                    resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
        } else {
            if (filter.isByUid()) {
                client.getUser(schema, filter.uid,
                        resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
            } else {
                client.getUser(schema, filter.name,
                        resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
            }
        }
    }
}
