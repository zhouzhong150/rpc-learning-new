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
package io.zz.rpc.loadbalancer.random;

import io.zz.rpc.loadbalancer.api.ServiceLoadBalancer;
import io.zz.rpc.protocol.meta.ServiceMeta;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@SPIClass
public class RandomServiceLoadBalancer<T> implements ServiceLoadBalancer<T> {

    private final Logger logger = LoggerFactory.getLogger(RandomServiceLoadBalancer.class);

    private Map<String, Long> isFall = new HashMap<>();



    @Override
    public T select(List<T> servers, int hashCode, String sourceIp, Long defaultFallbackTime) {
        List<T> candidate = new ArrayList<>();
        for (T server: servers){
            ServiceMeta server0 = (ServiceMeta) server;
            Long currentTime = System.currentTimeMillis();
            Long time = isFall.getOrDefault(server0.getServiceAddr() + ":" + server0.getServicePort(), null);
            if (time == null || (time == 0 || currentTime - time >= defaultFallbackTime)){
                candidate.add(server);
                isFall.put(server0.getServiceAddr() + ":" + server0.getServicePort(), 0L);
            }
        }
        if (candidate.size() == 0){
            candidate = servers;
        }

        logger.info("基于随机算法的负载均衡策略");
        if (candidate == null || candidate.isEmpty()){
            return null;
        }
        Random random = new Random();
        int index = random.nextInt(candidate.size());
        return candidate.get(index);
    }


    @Override
    public void changeStat(String ipPort){
        isFall.put(ipPort, System.currentTimeMillis());
    }
}
