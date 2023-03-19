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
package io.zz.rpc.consumer.common;


import io.zz.rpc.common.exception.RpcException;
import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.consumer.common.config.ConsumerConfig;
import io.zz.rpc.consumer.common.handler.RpcConsumerHandler;
import io.zz.rpc.consumer.common.helper.RpcConsumerHandlerHelper;
import io.zz.rpc.loadbalancer.api.ServiceLoadBalancer;
import io.zz.rpc.loadbalancer.context.ConnectionsContext;
import io.zz.rpc.protocol.meta.ServiceMeta;
import proxy.api.ProxyFactory;
import proxy.api.config.ProxyConfig;
import io.zz.rpc.registry.api.RegistryService;
import io.zz.rpc.spi.loader.ExtensionLoader;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

public class RpcClient {

    private final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    //当前重试次数
    private volatile int currentConnectRetryTimes = 0;

    /**
     * 服务版本
     */
    private String serviceVersion;
    /**
     * 服务分组
     */
    private String serviceGroup;
    /**
     * 序列化类型
     */
    private String serializationType;

    private ServiceLoadBalancer serviceLoadBalancer;

    /**
     * 超时时间
     */
    private long timeout;

    /**
     * 代理
     */
    private String proxy;

    //重试间隔时间
    private final int retryInterval = 1000;

    //重试次数
    private final int retryTimes = 3;

    /**
     * 是否开启直连服务
     */
    private boolean enableDirectServer;

    /**
     * 直连服务的地址
     */
    private String directServerUrl;

    /**
     * 是否开启延迟连接
     */
    private boolean enableDelayConnection;

    /**
     * 容错类Class名称
     */
    private String fallbackClassName;

    /**
     * 容错类
     */
    private Class<?> fallbackClass;

    /**
     * 反射类型
     */
    private String reflectType;

    /**
     * 熔断时间周期（负载均衡）
     */
    private Long defaultFallbackTime;

    private boolean initConnection;

    public RpcClient(ConsumerConfig consumerConfig, String proxy, String serviceVersion, String serviceGroup, String serializationType,
                     long timeout, boolean enableDirectServer, String directServerUrl, boolean enableDelayConnection,
                     String reflectType, String fallbackClassName,
                     String serviceLoadBalancerType, Long defaultFallbackTime) {
        this.serviceVersion = serviceVersion;
        this.proxy = proxy;
        this.timeout = timeout;
        this.serviceGroup = serviceGroup;
        this.serializationType = serializationType;
        this.enableDirectServer = enableDirectServer;
        this.directServerUrl = directServerUrl;
        this.enableDelayConnection = enableDelayConnection;
        this.reflectType = reflectType;
        this.fallbackClassName = fallbackClassName;
        RpcConsumer.getInstance(consumerConfig);
        this.serviceLoadBalancer = ExtensionLoader.getExtension(ServiceLoadBalancer.class, serviceLoadBalancerType);
        this.defaultFallbackTime = defaultFallbackTime;
        buildConnection();
    }


    public ServiceLoadBalancer getServiceLoadBalancer(){
        return this.serviceLoadBalancer;
    }

    public void setFallbackClass(Class<?> fallbackClass) {
        this.fallbackClass = fallbackClass;
    }

    public <T> T create(Class<T> interfaceClass) {
        ProxyFactory proxyFactory = ExtensionLoader.getExtension(ProxyFactory.class, proxy);
        proxyFactory.init(new ProxyConfig(interfaceClass, serviceVersion, serviceGroup, serializationType, timeout,
                this, reflectType, fallbackClassName, fallbackClass));
        return proxyFactory.getProxy(interfaceClass);
    }

    /**
     * 初始化连接
     */
    public void buildConnection(){
        //未开启延迟连接，并且未初始化连接
        if (!enableDelayConnection && !initConnection){
            List<ServiceMeta> serviceMetaList = new ArrayList<>();
            try{
                if (enableDirectServer){
                    serviceMetaList.add(this.getDirectServiceMetaWithCheck(directServerUrl));
                }
            }catch (Exception e){
                logger.error("init connection throws exception, the message is: {}", e.getMessage());
            }

            for(ServiceMeta serviceMeta : serviceMetaList){
                RpcConsumerHandler handler = null;
                try {
                    handler = this.getRpcConsumerHandler(serviceMeta);
                } catch (InterruptedException e) {
                    logger.error("call getRpcConsumerHandler() method throws InterruptedException, the message is: {}", e.getMessage());
                    continue;
                }
                RpcConsumerHandlerHelper.put(serviceMeta, handler);
            }
            this.initConnection = true;
        }
    }

    /**
     * 基于重试获取发送消息的handler
     */
    public RpcConsumerHandler getRpcConsumerHandlerWithRetry(RegistryService registryService, String serviceKey, int invokerHashCode) throws Exception{
        logger.info("获取服务消费者处理器...");
        RpcConsumerHandler handler = getRpcConsumerHandler(registryService, serviceKey, invokerHashCode);
        //获取的handler为空，启动重试机制
        if (handler == null){
            for (int i = 1; i <= retryTimes; i++){
                logger.info("获取服务消费者处理器第【{}】次重试...", i);
                handler = getRpcConsumerHandler(registryService, serviceKey, invokerHashCode);
                if (handler != null){
                    break;
                }
                Thread.sleep(retryInterval);
            }
        }
        return handler;
    }

    /**
     * 获取发送消息的handler
     */
    private RpcConsumerHandler getRpcConsumerHandler(RegistryService registryService, String serviceKey, int invokerHashCode) throws Exception {
        ServiceMeta serviceMeta = this.getDirectServiceMetaOrWithRetry(registryService, serviceKey, invokerHashCode);
        RpcConsumerHandler handler = null;
        if (serviceMeta != null){
            handler = getRpcConsumerHandlerWithRetry(serviceMeta);
        }
        return handler;
    }

    /**
     * 直连服务提供者或者结合重试获取服务元数据信息
     */
    public ServiceMeta getDirectServiceMetaOrWithRetry(RegistryService registryService, String serviceKey, int invokerHashCode) throws Exception {
        ServiceMeta serviceMeta = null;
        if (enableDirectServer){
            serviceMeta = this.getServiceMeta(directServerUrl);
        }else {
            serviceMeta = this.getServiceMetaWithRetry(registryService, serviceKey, invokerHashCode);
        }
        return serviceMeta;
    }

    /**
     * 直连服务提供者
     */
    private ServiceMeta getServiceMeta(String directServerUrl){
        logger.info("服务消费者直连服务提供者...");
        //只配置了一个服务提供者地址

        return getDirectServiceMetaWithCheck(directServerUrl);
    }

    /**
     * 服务消费者直连服务提供者
     */
    private ServiceMeta getDirectServiceMetaWithCheck(String directServerUrl){
        if (StringUtils.isEmpty(directServerUrl)){
            throw new RpcException("direct server url is null ...");
        }
        if (!directServerUrl.contains(RpcConstants.IP_PORT_SPLIT)){
            throw new RpcException("direct server url not contains : ");
        }
        return this.getDirectServiceMeta(directServerUrl);
    }

    /**
     * 获取直连服务提供者元数据
     */
    private ServiceMeta getDirectServiceMeta(String directServerUrl) {
        ServiceMeta serviceMeta = new ServiceMeta();
        String[] directServerUrlArray = directServerUrl.split(RpcConstants.IP_PORT_SPLIT);
        serviceMeta.setServiceAddr(directServerUrlArray[0].trim());
        serviceMeta.setServicePort(Integer.parseInt(directServerUrlArray[1].trim()));
        return serviceMeta;
    }

    /**
     * 重试获取服务提供者元数据
     */
    private ServiceMeta getServiceMetaWithRetry(RegistryService registryService, String serviceKey, int invokerHashCode) throws Exception {
        //首次获取服务元数据信息，如果获取到，则直接返回，否则进行重试
        logger.info("获取服务提供者元数据...");
        List<ServiceMeta> serviceMetaList = registryService.discovery(serviceKey);
        if (serviceMetaList == null || serviceMetaList.size() == 0){
            for (int i = 1; i <= retryTimes; i++){
                logger.info("获取服务提供者元数据第【{}】次重试...", i);
                serviceMetaList = registryService.discovery(serviceKey);
                if (serviceMetaList != null && serviceMetaList.size() != 0){
                    break;
                }
                Thread.sleep(retryInterval);
            }

        }
        ServiceMeta serviceMeta = (ServiceMeta) this.serviceLoadBalancer.select(serviceMetaList, invokerHashCode, RpcConsumer.getInstance(null).localIp(), defaultFallbackTime);
        return serviceMeta;
    }

    /**
     * 获取RpcConsumerHandler
     */
    public RpcConsumerHandler getRpcConsumerHandlerWithRetry(ServiceMeta serviceMeta) throws InterruptedException{
        logger.info("服务消费者连接服务提供者...");
        RpcConsumerHandler handler = null;
        try {
            handler = this.getRpcConsumerHandlerWithCache(serviceMeta);
        }catch (Exception e){
            //连接异常
            if (e instanceof ConnectException){
                //启动重试机制
                if (handler == null) {
                    if (currentConnectRetryTimes < retryTimes){
                        currentConnectRetryTimes++;
                        logger.info("服务消费者连接服务提供者第【{}】次重试...", currentConnectRetryTimes);
                        handler = this.getRpcConsumerHandlerWithRetry(serviceMeta);
                        Thread.sleep(retryInterval);
                    }
                }
            }
        }
        return handler;
    }

    /**
     * 从缓存中获取RpcConsumerHandler，缓存中没有，再创建
     */
    private RpcConsumerHandler getRpcConsumerHandlerWithCache(ServiceMeta serviceMeta) throws InterruptedException{
        RpcConsumerHandler handler = RpcConsumerHandlerHelper.get(serviceMeta);
        //缓存中无RpcClientHandler
        if (handler == null){
            handler = getRpcConsumerHandler(serviceMeta);
            RpcConsumerHandlerHelper.put(serviceMeta, handler);
        }else if (!handler.getChannel().isActive()){  //缓存中存在RpcClientHandler，但不活跃
            handler.close();
            handler = getRpcConsumerHandler(serviceMeta);
            RpcConsumerHandlerHelper.put(serviceMeta, handler);
        }
        return handler;
    }

    /**
     * 创建连接并返回RpcClientHandler
     */
    private RpcConsumerHandler getRpcConsumerHandler(ServiceMeta serviceMeta) throws InterruptedException {
        ChannelFuture channelFuture = RpcConsumer.getInstance(null).bootstrap().connect(serviceMeta.getServiceAddr(), serviceMeta.getServicePort()).sync();
        channelFuture.addListener((ChannelFutureListener) listener -> {
            if (channelFuture.isSuccess()) {
                logger.info("connect rpc server {} on port {} success.", serviceMeta.getServiceAddr(), serviceMeta.getServicePort());
                //添加连接信息，在服务消费者端记录每个服务提供者实例的连接次数
                ConnectionsContext.add(serviceMeta);
                //连接成功，将当前连接重试次数设置为0
                currentConnectRetryTimes = 0;
            } else {
                logger.error("connect rpc server {} on port {} failed.", serviceMeta.getServiceAddr(), serviceMeta.getServicePort());
                channelFuture.cause().printStackTrace();
                channelFuture.channel().close();
            }
        });
        return channelFuture.channel().pipeline().get(RpcConsumerHandler.class);
    }

}
