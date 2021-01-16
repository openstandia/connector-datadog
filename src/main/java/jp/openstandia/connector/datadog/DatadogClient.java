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

import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

/**
 * DatadogClient interface.
 *
 * @author Hiroyuki Wada
 */
public interface DatadogClient {
    void test();

    // User

    Uid createUser(DatadogSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException;

    void updateUser(DatadogSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException;

    void deleteUser(DatadogSchema schema, Uid uid, OperationOptions options) throws UnknownUidException;

    void getUsers(DatadogSchema schema, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    void getUser(DatadogSchema schema, Uid uid, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    void getUser(DatadogSchema schema, Name name, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    // Role

    Uid createRole(DatadogSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException;

    void updateRole(DatadogSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException;

    void deleteRole(DatadogSchema schema, Uid uid, OperationOptions options) throws UnknownUidException;

    void getRoles(DatadogSchema schema, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    void getRole(DatadogSchema schema, Uid uid, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    void getRole(DatadogSchema schema, Name name, ResultsHandler handler, OperationOptions options, Set<String> attributesToGet, long queryPageSize);

    void close();
}

