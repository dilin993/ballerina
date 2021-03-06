/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.net.http.actions.websocketconnector;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.natives.annotations.ReturnType;
import org.ballerinalang.net.http.WebSocketConstants;
import org.ballerinalang.net.http.WebSocketUtil;
import org.ballerinalang.util.exceptions.BallerinaException;

/**
 * {@code Get} is the GET action implementation of the HTTP Connector.
 */
@BallerinaFunction(
        packageName = "ballerina.net.http",
        functionName = "getQueryParams",
        receiver = @Receiver(type = TypeKind.STRUCT, structType = WebSocketConstants.WEBSOCKET_CONNECTOR,
                structPackage = "ballerina.net.http"),
        args = {
                @Argument(name = "wsConnector", type = TypeKind.STRUCT)
        },
        returnType = {
                @ReturnType(type = TypeKind.MAP, elementType = TypeKind.STRING)
        }
)
public class GetQueryParams extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context context) {
        try {
            context.setReturnValues(WebSocketUtil.getQueryParams(context));
        } catch (Throwable e) {
            throw new BallerinaException(
                    "Error occurred while retrieving query parameters from Connection: " + e.getMessage());
        }
    }
}
