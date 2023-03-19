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
package io.zz.rpc.loadbalancer.sourceip.hash;

import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.loadbalancer.api.ServiceLoadBalancer;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SPIClass
public class SourceIpHashServiceLoadBalancer<T> implements ServiceLoadBalancer<T> {
    private final Logger logger = LoggerFactory.getLogger(SourceIpHashServiceLoadBalancer.class);

    private Map<String, Long> isFall = new HashMap<>();

    @Override
    public T select(List<T> servers, int hashCode, String sourceIp, Long defaultFallbackTime) {
        logger.info("基于源IP地址Hash的负载均衡策略...");
        if (servers == null || servers.isEmpty()){
            return null;
        }
        //传入的IP地址为空，则默认返回第一个服务实例
        if (StringUtils.isEmpty(sourceIp)){
            return servers.get(0);
        }
        int resultHashCode = Math.abs(sourceIp.hashCode() + hashCode);
        return servers.get(resultHashCode % servers.size());
    }

    @Override
    public void changeStat(String ipPort){
        isFall.put(ipPort, System.currentTimeMillis());
    }
}
