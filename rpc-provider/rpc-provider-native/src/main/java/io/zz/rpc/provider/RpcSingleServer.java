/**
 * Copyright 2020-9999 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zz.rpc.provider;

import io.zz.rpc.provider.common.scanner.RpcServiceScanner;
import io.zz.rpc.provider.common.server.base.BaseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcSingleServer extends BaseServer {

    private final Logger logger = LoggerFactory.getLogger(RpcSingleServer.class);

    public RpcSingleServer(String serverAddress, String serverRegistryAddress, String registryAddress, String registryType,
                           String scanPackage, String reflectType, int heartbeatInterval,
                           int scanNotActiveChannelInterval, int resultCacheExpire,
                           int corePoolSize, int maximumPoolSize, String flowType, int maxConnections, String disuseStrategyType,
                           boolean enableBuffer, int bufferSize, String exceptionPostProcessorType) {
        //调用父类构造方法
        super(serverAddress, serverRegistryAddress, registryAddress, registryType, reflectType,
                heartbeatInterval, scanNotActiveChannelInterval, resultCacheExpire, corePoolSize,
                maximumPoolSize, flowType, maxConnections, disuseStrategyType, enableBuffer, bufferSize, exceptionPostProcessorType);
        try {
            this.handlerMap = RpcServiceScanner.doScannerWithRpcServiceAnnotationFilterAndRegistryService(this.serverRegistryHost, this.serverRegistryPort, scanPackage, registryService);
        } catch (Exception e) {
            logger.error("RPC Server init error", e);
        }
    }
}
