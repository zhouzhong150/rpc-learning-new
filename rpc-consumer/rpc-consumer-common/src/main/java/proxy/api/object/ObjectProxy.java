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
package proxy.api.object;


import io.zz.rpc.annotation.RpcReferenceMethod;
import io.zz.rpc.cache.result.CacheResultKey;
import io.zz.rpc.cache.result.CacheResultManager;
import io.zz.rpc.common.exception.FailException;
import io.zz.rpc.common.exception.FallBackException;
import io.zz.rpc.common.helper.RpcServiceHelper;
import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.consumer.common.RpcClient;
import io.zz.rpc.consumer.common.RpcConsumer;
import io.zz.rpc.consumer.common.handler.RpcConsumerHandler;
import io.zz.rpc.fusing.api.FusingInvoker;
import io.zz.rpc.fusing.counter.CounterFusingInvoker;
import io.zz.rpc.fusing.percent.PercentFusingInvoker;
import io.zz.rpc.protocol.meta.ServiceMeta;
import io.zz.rpc.protocol.response.RpcResponse;
import io.zz.rpc.reflect.api.ReflectInvoker;
import proxy.api.future.RPCFuture;
import io.zz.rpc.protocol.RpcProtocol;
import io.zz.rpc.protocol.enumeration.RpcType;
import io.zz.rpc.protocol.header.RpcHeaderFactory;
import io.zz.rpc.protocol.request.RpcRequest;
import io.zz.rpc.registry.api.RegistryService;
import io.zz.rpc.spi.loader.ExtensionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class ObjectProxy <T> implements InvocationHandler{
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectProxy.class);

    /**
     * 接口的Class对象
     */
    private Class<T> clazz;

    /**
     * 服务版本号
     */
    private String serviceVersion;
    /**
     * 服务分组
     */
    private String serviceGroup;
    /**
     * 超时时间，默认15s
     */
    private long timeout = 15000;

    /**
     * 注册服务
     */
    private RegistryService registryService;

    /**
     * 服务消费者
     */
    private RpcClient rpcClient;

    /**
     * 序列化类型
     */
    private String serializationType;

    /**
     * 结果缓存管理器
     */
    private CacheResultManager<Object> cacheResultManager;

    /**
     * 反射调用方法
     */
    private ReflectInvoker reflectInvoker;

    /**
     * 容错Class类
     */
    private Class<?> fallbackClass;


    public ObjectProxy(Class<T> clazz, String serviceVersion, String serviceGroup, String serializationType, long timeout,
                       RpcClient rpcClient, String reflectType, String fallbackClassName, Class<?> fallbackClass) {
        this.clazz = clazz;
        this.serviceVersion = serviceVersion;
        this.timeout = timeout;
        this.serviceGroup = serviceGroup;
        this.rpcClient = rpcClient;
        this.serializationType = serializationType;

        this.reflectInvoker = ExtensionLoader.getExtension(ReflectInvoker.class, reflectType);
        this.fallbackClass = this.getFallbackClass(fallbackClassName, fallbackClass);
    }

    /**
     * 优先使用fallbackClass，如果fallbackClass为空，则使用fallbackClassName
     */
    private Class<?> getFallbackClass(String fallbackClassName, Class<?> fallbackClass) {
        if (this.isFallbackClassEmpty(fallbackClass)){
            try {
                if (!StringUtils.isEmpty(fallbackClassName)){
                    fallbackClass = Class.forName(fallbackClassName);
                }
            } catch (ClassNotFoundException e) {
                RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(e);
                LOGGER.error(e.getMessage());
            }
        }
        return fallbackClass;
    }

    /**
     * 容错class为空
     */
    private boolean isFallbackClassEmpty(Class<?> fallbackClass){
        return fallbackClass == null
                || fallbackClass == RpcConstants.DEFAULT_FALLBACK_CLASS
                || RpcConstants.DEFAULT_FALLBACK_CLASS.equals(fallbackClass);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }

        RpcReferenceMethod rpcReferenceMethod = method.getAnnotation(RpcReferenceMethod.class);
        Integer hashCode = null;
        if (rpcReferenceMethod != null) {

            CacheResultKey cacheKey = new CacheResultKey(method.getDeclaringClass().getName(), method.getName(), method.getParameterTypes(), new Object[0], serviceVersion, serviceGroup);
            hashCode = cacheKey.hashCode();
            this.cacheResultManager = CacheResultManager.getInstance(RpcConstants.RPC_SCAN_RESULT_CACHE_EXPIRE);

        }

        //默认值
        boolean enableResultCache = false;
        boolean oneway = false;
        if (hashCode != null) {
            enableResultCache = rpcReferenceMethod.enableResultCache();
            oneway = rpcReferenceMethod.oneway();
        }

        //开启缓存，直接调用方法请求服务提供者
        if (enableResultCache && !oneway) {return invokeSendRequestMethodCache(method, args, oneway, hashCode);}
        return invokeSendRequestMethodWithFallback(method, args, oneway, hashCode);
    }

    private Object invokeSendRequestMethodCache(Method method, Object[] args, boolean oneway, Integer hashCode) throws Exception {
        //开启缓存，则处理缓存
        CacheResultKey cacheResultKey = new CacheResultKey(method.getDeclaringClass().getName(), method.getName(), method.getParameterTypes(), args, serviceVersion, serviceGroup);
        Object obj = RpcConsumer.getInstance(null).cacheResultManager().get(cacheResultKey);
        if (obj == null){
            obj = invokeSendRequestMethodWithFallback(method, args, oneway, hashCode);
            if (obj != null){
                cacheResultKey.setCacheTimeStamp(System.currentTimeMillis());
                RpcConsumer.getInstance(null).cacheResultManager().put(cacheResultKey, obj);
            }
        }
        return obj;
    }

    /**
     * 以容错方式真正发送请求
     */
    private Object invokeSendRequestMethodWithFallback(Method method, Object[] args, boolean oneway, Integer hashCode) throws Exception {
        try {
            return invokeSendRequestMethodWithFsing(method, args, oneway, hashCode);
        }catch (FallBackException e){
            //降级处理
            RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(e);
            return getFallbackResult(method, args);
        }catch (FailException e){
            //异常处理
            RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(e);
            return getFallbackResult(method, args);
        }catch (Throwable e){
            //其他异常处理
            RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(e);
            return getFallbackResult(method, args);
        }
    }

    /**
     * 以熔断方式请求数据
     */
    private Object invokeSendRequestMethodWithFsing(Method method, Object[] args, boolean oneway, Integer hashCode) throws Exception{
        //默认值
        boolean enableFusing = false;
        RpcReferenceMethod rpcReferenceMethod = method.getAnnotation(RpcReferenceMethod.class);
        if (rpcReferenceMethod != null) {
            enableFusing = rpcReferenceMethod.enableFusing();
        }
        if (enableFusing){
            return invokeFusingSendRequestMethod(method, args, oneway, hashCode);
        }else {
            return invokeSendRequestMethod(method, args, oneway);
        }
    }

    /**
     * 熔断请求
     */
    private Object invokeFusingSendRequestMethod(Method method, Object[] args, boolean oneway, Integer hashCode) throws Exception {
        //默认值
        FusingInvoker fusingInvoker = null;
        if (hashCode != null) {
            fusingInvoker = RpcConsumer.getFusingInvoker(hashCode);
            RpcReferenceMethod rpcReferenceMethod = method.getAnnotation(RpcReferenceMethod.class);
            if (fusingInvoker == null && rpcReferenceMethod != null) {
                String fusingType = rpcReferenceMethod.fusingType();
                switch (fusingType){
                    case "counter":
                        fusingInvoker = new CounterFusingInvoker();
                        break;
                    case "percent":
                        fusingInvoker = new PercentFusingInvoker();
                        break;
                    //todo
                }
                fusingInvoker.init(rpcReferenceMethod.totalFailure(), rpcReferenceMethod.fusingMilliSeconds());
                RpcConsumer.updateRpcFusingInvokerMap(hashCode, fusingInvoker);
            }
        }
        //触发了熔断规则，直接返回降级处理业务
        if (fusingInvoker.invokeFusingStrategy()){
            return this.getFallbackResult(method, args);
        }
        //请求计数器加1
        fusingInvoker.incrementCount();
        Object result = null;
        try {
            result = invokeSendRequestMethod(method, args, oneway);
            fusingInvoker.markSuccess();
        }catch (Throwable e){
            RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(e);
            fusingInvoker.markFailed();
            throw e;
        }
        return result;
    }


    /**
     * 真正发送请求调用远程方法
     */
    private Object invokeSendRequestMethod(Method method, Object[] args, boolean oneway) throws Exception {
        RpcProtocol<RpcRequest> requestRpcProtocol = getSendRequest(method, args, oneway);

        RpcRequest request = requestRpcProtocol.getBody();
        String serviceKey = RpcServiceHelper.buildServiceKey(request.getClassName(), request.getVersion(), request.getGroup());
        Object[] params = request.getParameters();
        int invokerHashCode =  (params == null || params.length <= 0) ? serviceKey.hashCode() : params[0].hashCode();
        //获取发送消息的handler

        ServiceMeta serviceMeta = rpcClient.getDirectServiceMetaOrWithRetry(registryService, serviceKey, invokerHashCode);
        RpcConsumerHandler handler = null;
        if (serviceMeta != null){
            handler = this.rpcClient.getRpcConsumerHandlerWithRetry(serviceMeta);
        }
        RPCFuture rpcFuture = null;
        if (handler != null){
            rpcFuture = handler.sendRequest(requestRpcProtocol, request.getOneway());
            try{
                if (oneway){
                    return null;
                }
                //out
                RpcResponse res = (RpcResponse) rpcFuture.get(timeout, TimeUnit.MILLISECONDS);
                if (! res.getError().equals("")){
                    rpcClient.getServiceLoadBalancer().changeStat(serviceMeta.getServiceAddr() + ":" + serviceMeta.getServicePort());
                }
                return res.getResult();
            }catch (Exception e){
                rpcClient.getServiceLoadBalancer().changeStat(serviceMeta.getServiceAddr() + ":" + serviceMeta.getServicePort());
                throw e;
            }
        }

        throw new RuntimeException();
    }

    /**
     * 获取容错结果
     */
    private Object getFallbackResult(Method method, Object[] args) {
        try {
            //fallbackClass不为空，则执行容错处理
            if (this.isFallbackClassEmpty(fallbackClass)){
                return null;
            }
            return reflectInvoker.invokeMethod(fallbackClass.newInstance(), fallbackClass, method.getName(), method.getParameterTypes(), args);
        } catch (Throwable ex) {
            RpcConsumer.getInstance(null).getExceptionPostProcessor().postExceptionProcessor(ex);
            LOGGER.error(ex.getMessage());
        }
        return null;
    }

    /**
     * 封装请求协议对象
     */
    private RpcProtocol<RpcRequest> getSendRequest(Method method, Object[] args, boolean oneway) {
        RpcProtocol<RpcRequest> requestRpcProtocol = new RpcProtocol<RpcRequest>();

        requestRpcProtocol.setHeader(RpcHeaderFactory.getRequestHeader(serializationType, RpcType.REQUEST.getType()));

        RpcRequest request = new RpcRequest();
        request.setVersion(this.serviceVersion);
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setGroup(this.serviceGroup);
        request.setParameters(args);
        request.setOneway(oneway);
        requestRpcProtocol.setBody(request);

        // Debug
        LOGGER.debug(method.getDeclaringClass().getName());
        LOGGER.debug(method.getName());

        if (method.getParameterTypes() != null && method.getParameterTypes().length > 0){
            for (int i = 0; i < method.getParameterTypes().length; ++i) {
                LOGGER.debug(method.getParameterTypes()[i].getName());
            }
        }

        if (args != null && args.length > 0){
            for (int i = 0; i < args.length; ++i) {
                LOGGER.debug(args[i].toString());
            }
        }
        return requestRpcProtocol;
    }

}
