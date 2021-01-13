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

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.operations.SearchOp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Schema for Datadog objects.
 *
 * @author Hiroyuki Wada
 */
public class DatadogSchema {

    private final DatadogConfiguration configuration;
    private final DatadogClient client;

    public final Schema schema;
    public final Map<String, AttributeInfo> userSchema;

    public DatadogSchema(DatadogConfiguration configuration, DatadogClient client) {
        this.configuration = configuration;
        this.client = client;

        SchemaBuilder schemaBuilder = new SchemaBuilder(DatadogConnector.class);

        ObjectClassInfo userSchemaInfo = DatadogUserHandler.getUserSchema();
        schemaBuilder.defineObjectClass(userSchemaInfo);

        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(), SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildReturnDefaultAttributes(), SearchOp.class);

        schema = schemaBuilder.build();

        Map<String, AttributeInfo> userSchemaMap = new HashMap<>();
        for (AttributeInfo info : userSchemaInfo.getAttributeInfo()) {
            userSchemaMap.put(info.getName(), info);
        }

        this.userSchema = Collections.unmodifiableMap(userSchemaMap);
    }

    public boolean isUserSchema(Attribute attribute) {
        return userSchema.containsKey(attribute.getName());
    }

    public boolean isMultiValuedUserSchema(Attribute attribute) {
        return userSchema.get(attribute.getName()).isMultiValued();
    }

    public boolean isUserSchema(AttributeDelta delta) {
        return userSchema.containsKey(delta.getName());
    }

    public boolean isMultiValuedUserSchema(AttributeDelta delta) {
        return userSchema.get(delta.getName()).isMultiValued();
    }

    public AttributeInfo getUserSchema(String attributeName) {
        return userSchema.get(attributeName);
    }
}
