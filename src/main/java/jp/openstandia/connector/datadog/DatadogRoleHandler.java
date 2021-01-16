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
 * Handle datadog role object.
 * <p>
 * Caution! Can't use Create/Update/Delete operations with default setting.
 * To use them, enable custom role feature in your datadog organization which is opt-in enterprise feature.
 *
 * @author Hiroyuki Wada
 */
public class DatadogRoleHandler extends AbstractDatadogHandler {

    public static final ObjectClass ROLE_OBJECT_CLASS = new ObjectClass("role");

    private static final Log LOGGER = Log.getLog(DatadogRoleHandler.class);

    // Unique and unchangeable.
    // Don't use "id" here because it conflicts midpoint side.
    private static final String ATTR_ROLE_ID = "roleId";

    // Unique.
    private static final String ATTR_NAME = "name";

    // Readonly
    public static final String ATTR_CREATED_AT = "createdAt";
    public static final String ATTR_MODIFIED_AT = "modifiedAt";
    public static final String ATTR_USER_COUNT = "userCount";

    public DatadogRoleHandler(String instanceName, DatadogConfiguration configuration, DatadogClient client,
                              DatadogSchema schema) {
        super(instanceName, configuration, client, schema);
    }

    public static ObjectClassInfo getRoleSchema() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ROLE_OBJECT_CLASS.getObjectClassValue());

        // id (__UID__)
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Uid.NAME)
                        .setRequired(false) // Must be optional. It is not present for create operations
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .setNativeName(ATTR_ROLE_ID)
                        .build());

        // email (__NAME__)
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Name.NAME)
                        .setRequired(true)
                        .setNativeName(ATTR_NAME)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .build());

        // Readonly
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_CREATED_AT)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setType(ZonedDateTime.class)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_MODIFIED_AT)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setType(ZonedDateTime.class)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_USER_COUNT)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setType(Long.class)
                        .build());

        ObjectClassInfo schemaInfo = builder.build();

        LOGGER.ok("The constructed datadog role schema: {0}", schemaInfo);

        return schemaInfo;
    }

    /**
     * @param attributes
     * @return
     */
    @Override
    public Uid create(Set<Attribute> attributes) {
        return client.createRole(schema, attributes);
    }

    /**
     * @param uid
     * @param modifications
     * @param options
     * @return
     */
    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        client.updateRole(schema, uid, modifications, options);

        return null;
    }

    /**
     * @param uid
     * @param options
     */
    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteRole(schema, uid, options);
    }

    @Override
    public void query(DatadogFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        // Create full attributesToGet by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET
        Set<String> attributesToGet = createFullAttributesToGet(schema.roleSchema, options);

        if (filter == null) {
            client.getRoles(schema,
                    resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
        } else {
            if (filter.isByUid()) {
                client.getRole(schema, filter.uid,
                        resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
            } else {
                client.getRole(schema, filter.name,
                        resultsHandler, options, attributesToGet, configuration.getQueryPageSize());
            }
        }
    }
}
