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
package io.zz.rpc.provider.spring;

import io.zz.rpc.annotation.RpcService;
import io.zz.rpc.annotation.RpcServiceMethod;
import io.zz.rpc.cache.result.CacheResultKey;
import io.zz.rpc.common.helper.RpcServiceHelper;
import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.fusing.api.FusingInvoker;
import io.zz.rpc.fusing.counter.CounterFusingInvoker;
import io.zz.rpc.fusing.percent.PercentFusingInvoker;
import io.zz.rpc.protocol.meta.ServiceMeta;
import io.zz.rpc.provider.common.server.base.BaseServer;
import io.zz.rpc.ratelimiter.api.RateLimiterInvoker;
import io.zz.rpc.ratelimiter.counter.CounterRateLimiterInvoker;
import io.zz.rpc.ratelimiter.guava.GuavaRateLimiterInvoker;
import io.zz.rpc.ratelimiter.semaphore.SemaphoreRateLimiterInvoker;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.Map;

public class RpcSpringServer extends BaseServer implements ApplicationContextAware, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(RpcSpringServer.class);

    public RpcSpringServer(String serverAddress, String serverRegistryAddress, String registryAddress, String registryType,
                           String reflectType, int heartbeatInterval, int scanNotActiveChannelInterval,
                           int resultCacheExpire, int corePoolSize, int maximumPoolSize, String flowType,
                           int maxConnections, String disuseStrategyType, boolean enableBuffer, int bufferSize, String exceptionPostProcessorType) {
        super(serverAddress, serverRegistryAddress, registryAddress, registryType, reflectType,
                heartbeatInterval, scanNotActiveChannelInterval, resultCacheExpire, corePoolSize,
                maximumPoolSize, flowType, maxConnections, disuseStrategyType, enableBuffer, bufferSize, exceptionPostProcessorType);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                RpcService rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
                ServiceMeta serviceMeta = new ServiceMeta(this.getServiceName(rpcService), rpcService.version(), rpcService.group(), serverRegistryHost, serverRegistryPort, getWeight(rpcService.weight()));
                handlerMap.put(RpcServiceHelper.buildServiceKey(serviceMeta.getServiceName(), serviceMeta.getServiceVersion(), serviceMeta.getServiceGroup()), serviceBean);
                try {
                    registryService.register(serviceMeta);

                    Method[] methods = serviceBean.getClass().getMethods();
                    for (Method method: methods) {
                        RpcServiceMethod rpcServiceMethod = method.getAnnotation(RpcServiceMethod.class);
                        if (rpcServiceMethod != null){
                            CacheResultKey cacheKey = new CacheResultKey(serviceBean.getClass().getName(), method.getName(), method.getParameterTypes(), new Object[0], rpcService.version(), rpcService.group());
                            //将注解放进map
                            BaseServer.updateAnnotation(cacheKey.hashCode(), rpcServiceMethod);
                            //初始化限流器，放进map
                            boolean enableRateLimiter = rpcServiceMethod.enableRateLimiter();
                            String rateLimiterType = rpcServiceMethod.rateLimiterType();
                            int permits = rpcServiceMethod.permits();
                            int milliSeconds = rpcServiceMethod.milliSeconds();
                            if (enableRateLimiter) {
                                rateLimiterType = StringUtils.isEmpty(rateLimiterType) ? RpcConstants.DEFAULT_RATELIMITER_INVOKER : rateLimiterType;
                                RateLimiterInvoker rateLimiterInvoker = null;
                                switch (rateLimiterType){
                                    case "counter":
                                        rateLimiterInvoker = new CounterRateLimiterInvoker();
                                        break;
                                    case "guava":
                                        rateLimiterInvoker = new GuavaRateLimiterInvoker();
                                        break;
                                    case "semaphore":
                                        rateLimiterInvoker = new SemaphoreRateLimiterInvoker();
                                        break;
                                    //todo
                                }
                                rateLimiterInvoker.init(permits, milliSeconds);
                                BaseServer.updateRpcRateLimiterInvokerMap(cacheKey.hashCode(), rateLimiterInvoker);
                            }
                            //初始化熔断器，放进map
                            boolean enableFusing = rpcServiceMethod.enableFusing();
                            String fusingType = rpcServiceMethod.fusingType();
                            double totalFailure = rpcServiceMethod.totalFailure();
                            int fusingMilliSeconds = rpcServiceMethod.fusingMilliSeconds();
                            if (enableFusing){
                                fusingType = StringUtils.isEmpty(fusingType) ? RpcConstants.DEFAULT_FUSING_INVOKER : fusingType;
                                FusingInvoker fusingInvoker = null;
                                switch (fusingType){
                                    case "counter":
                                        fusingInvoker = new CounterFusingInvoker();
                                        break;
                                    case "percent":
                                        fusingInvoker = new PercentFusingInvoker();
                                        break;
                                    //todo
                                }
                                fusingInvoker.init(totalFailure, fusingMilliSeconds);
                                BaseServer.updateRpcFusingInvokerMap(cacheKey.hashCode(), fusingInvoker);
                            }
                        }
                    }
                }catch (Exception e){
                    logger.error("rpc server init spring exception:{}", e);
                }
            }
        }
    }

    private int getWeight(int weight) {
        if (weight < RpcConstants.SERVICE_WEIGHT_MIN){
            weight = RpcConstants.SERVICE_WEIGHT_MIN;
        }
        if (weight > RpcConstants.SERVICE_WEIGHT_MAX){
            weight = RpcConstants.SERVICE_WEIGHT_MAX;
        }
        return weight;
    }

    /**
     * 获取serviceName
     */
    private String getServiceName(RpcService rpcService){
        //优先使用interfaceClass
        Class clazz = rpcService.interfaceClass();
        if (clazz == null || clazz == void.class){
            return rpcService.interfaceClassName();
        }
        String serviceName = clazz.getName();
        if (serviceName == null || serviceName.trim().isEmpty()){
            serviceName = rpcService.interfaceClassName();
        }
        return serviceName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.startNettyServer();
    }
}
