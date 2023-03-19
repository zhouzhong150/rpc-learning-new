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
package io.zz.rpc.provider.common.scanner;

import io.zz.rpc.annotation.RpcServiceMethod;
import io.zz.rpc.annotation.RpcService;
import io.zz.rpc.cache.result.CacheResultKey;
import io.zz.rpc.common.helper.RpcServiceHelper;
import io.zz.rpc.common.scanner.ClassScanner;
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
import io.zz.rpc.registry.api.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcServiceScanner extends ClassScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServiceScanner.class);

    /**
     * 扫描指定包下的类，并筛选使用@RpcService注解标注的类
     */
    public static Map<String, Object> doScannerWithRpcServiceAnnotationFilterAndRegistryService(String host, int port, String scanPackage, RegistryService registryService) throws Exception{
        Map<String, Object> handlerMap = new HashMap<>();
        List<String> classNameList = getClassNameList(scanPackage, true);
        if (classNameList == null || classNameList.isEmpty()){
            return handlerMap;
        }
        classNameList.stream().forEach((className) -> {
            try {
                Class<?> clazz = Class.forName(className);
                RpcService rpcService = clazz.getAnnotation(RpcService.class);
                if (rpcService != null){
                    //优先使用interfaceClass, interfaceClass的name为空，再使用interfaceClassName
                    ServiceMeta serviceMeta = new ServiceMeta(getServiceName(rpcService), rpcService.version(), rpcService.group(), host, port, getWeight(rpcService.weight()));
                    //将元数据注册到注册中心
                    registryService.register(serviceMeta);
                    handlerMap.put(RpcServiceHelper.buildServiceKey(serviceMeta.getServiceName(), serviceMeta.getServiceVersion(), serviceMeta.getServiceGroup()), clazz.newInstance());
                    Method[] methods = clazz.getMethods();
                    for (Method method: methods) {
                        RpcServiceMethod rpcServiceMethod = method.getAnnotation(RpcServiceMethod.class);
                        if (rpcServiceMethod != null){
                            CacheResultKey cacheKey = new CacheResultKey(clazz.getName(), method.getName(), method.getParameterTypes(), new Object[0], rpcService.version(), rpcService.group());
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
                                    case "count":
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
                }
            } catch (Exception e) {
                LOGGER.error("scan classes throws exception: {}", e);
            }
        });
        return handlerMap;
    }

    private static int getWeight(int weight) {
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
    private static String getServiceName(RpcService rpcService){
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
}
