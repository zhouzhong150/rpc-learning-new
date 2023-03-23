/**
 * Copyright 2022-9999 the original author or authors.
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
package io.zz.rpc.disuse.lfu;

import io.zz.rpc.disuse.api.DisuseStrategy;
import io.zz.rpc.disuse.api.connection.ConnectionInfo;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SPIClass
public class LfuDisuseStrategy implements DisuseStrategy {
    private final Logger logger = LoggerFactory.getLogger(LfuDisuseStrategy.class);
    private final Comparator<ConnectionInfo> useCountComparator = (o1, o2) -> {
        return o1.getUseCount() - o2.getUseCount() > 0 ? 1 : -1;
    };
    @Override
    public ConnectionInfo selectConnection(List<ConnectionInfo> connectionList) {
        logger.info("execute lfu disuse strategy...");
        if (connectionList.isEmpty()) return null;
        Collections.sort(connectionList, useCountComparator);
        return connectionList.get(0);
    }
}
