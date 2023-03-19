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


import io.zz.rpc.cache.result.CacheResultManager;
import io.zz.rpc.common.exception.RegistryException;
import io.zz.rpc.common.exception.RpcException;
import io.zz.rpc.common.helper.RpcServiceHelper;
import io.zz.rpc.common.ip.IpUtils;
import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.consumer.common.config.ConsumerConfig;
import io.zz.rpc.consumer.common.helper.RpcConsumerHandlerHelper;
import io.zz.rpc.consumer.common.initializer.RpcConsumerInitializer;
import io.zz.rpc.consumer.common.manager.ConsumerConnectionManager;
import io.zz.rpc.exception.processor.ExceptionPostProcessor;
import io.zz.rpc.flow.processor.FlowPostProcessor;
import io.zz.rpc.fusing.api.FusingInvoker;
import io.zz.rpc.loadbalancer.context.ConnectionsContext;
import io.zz.rpc.protocol.RpcProtocol;
import io.zz.rpc.protocol.meta.ServiceMeta;
import io.zz.rpc.protocol.request.RpcRequest;
import io.zz.rpc.registry.api.RegistryService;
import io.zz.rpc.registry.api.config.RegistryConfig;
import io.zz.rpc.spi.loader.ExtensionLoader;
import io.zz.rpc.threadpool.ConcurrentThreadPool;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RpcConsumer {

    private final Logger logger = LoggerFactory.getLogger(RpcConsumer.class);
    private Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final String localIp;
    private static volatile RpcConsumer instance;
    private ScheduledExecutorService executorService;

    //心跳间隔时间，默认30秒
    private int heartbeatInterval = 30000;

    //扫描并移除空闲连接时间，默认60秒
    private int scanNotActiveChannelInterval = 60000;

    //重试间隔时间
    private int retryInterval = 1000;

    //重试次数
    private int retryTimes = 3;

    //当前重试次数
    private volatile int currentConnectRetryTimes = 0;

    private RegistryService registryService;

    //是否开启直连服务
    private boolean enableDirectServer = false;

    //直连服务的地址
    private String directServerUrl;

    //是否开启延迟连接
    private boolean enableDelayConnection = false;

    //未开启延迟连接时，是否已经初始化连接
    private volatile boolean initConnection = false;

    //并发处理线程池
    private ConcurrentThreadPool concurrentThreadPool;

    //流控分析后置处理器
    private FlowPostProcessor flowPostProcessor;
    //异常处理后置处理器
    private ExceptionPostProcessor exceptionPostProcessor;
    //是否开启数据缓冲
    private boolean enableBuffer;
    //缓冲区大小
    private int bufferSize;
    CacheResultManager cacheResultManager;

    //存储方法对应的熔断器
    private static ConcurrentHashMap<Integer, FusingInvoker> fusingInvokerMap;

    private RpcConsumer(ConsumerConfig consumerConfig) {
        localIp = IpUtils.getLocalHostIp();
        bootstrap = new Bootstrap();
        eventLoopGroup = new NioEventLoopGroup(4);
        readResource(consumerConfig);
        startHeartbeat();
        buildNettyGroup();
    }


    private void readResource(ConsumerConfig consumerConfig){
            this.heartbeatInterval = consumerConfig.getHeartbeatInterval();
            this.scanNotActiveChannelInterval = consumerConfig.getScanNotActiveChannelInterval();
            this.retryInterval = consumerConfig.getRetryInterval();
            this.retryTimes = consumerConfig.getRetryTimes();
            setFlowPostProcessor(consumerConfig.getFlowType());
            this.enableBuffer = consumerConfig.getEnableBuffer();
            this.bufferSize = consumerConfig.getBufferSize();
            setExceptionPostProcessor(consumerConfig.getExceptionPostProcessorType());
            String registryAddress = consumerConfig.getRegistryAddress();
            String registryType = consumerConfig.getRegistryType();
            this.registryService = getRegistryService(registryAddress, registryType);
            int corePoolSize = consumerConfig.getCorePoolSize();
            int maximumPoolSize = consumerConfig.getMaximumPoolSize();
            this.concurrentThreadPool = ConcurrentThreadPool.getInstance(corePoolSize, maximumPoolSize);
            int resultCacheExpire = consumerConfig.getResultCacheExpire();
            if (resultCacheExpire <= 0){
                resultCacheExpire = RpcConstants.RPC_SCAN_RESULT_CACHE_EXPIRE;
            }
            cacheResultManager = CacheResultManager.getInstance(resultCacheExpire);
    }

    public CacheResultManager cacheResultManager(){
        return cacheResultManager;
    }

    public RegistryService registryService(){
        return registryService;
    }

    public Bootstrap bootstrap(){
        return bootstrap;
    }

    public String localIp(){
        return localIp;
    }

    private RegistryService getRegistryService(String registryAddress, String registryType) {
        if (org.springframework.util.StringUtils.isEmpty(registryType)){
            throw new IllegalArgumentException("registry type is null");
        }
        RegistryService registryService = ExtensionLoader.getExtension(RegistryService.class, registryType);
        try {
            registryService.init(new RegistryConfig(registryAddress, registryType));
        } catch (Exception e) {
            logger.error("RpcClient init registry service throws exception:{}", e);
            throw new RegistryException(e.getMessage(), e);
        }
        return registryService;
    }

    public RpcConsumer setEnableDelayConnection(boolean enableDelayConnection) {
        this.enableDelayConnection = enableDelayConnection;
        return this;
    }

    public RpcConsumer setEnableDirectServer(boolean enableDirectServer) {
        this.enableDirectServer = enableDirectServer;
        return this;
    }

    public RpcConsumer setDirectServerUrl(String directServerUrl) {
        this.directServerUrl = directServerUrl;
        return this;
    }

    public RpcConsumer setHeartbeatInterval(int heartbeatInterval) {
        if (heartbeatInterval > 0){
            this.heartbeatInterval = heartbeatInterval;
        }
        return this;
    }

    public RpcConsumer setScanNotActiveChannelInterval(int scanNotActiveChannelInterval) {
        if (scanNotActiveChannelInterval > 0){
            this.scanNotActiveChannelInterval = scanNotActiveChannelInterval;
        }
        return this;
    }

    public RpcConsumer setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval <= 0 ? RpcConstants.DEFAULT_RETRY_INTERVAL : retryInterval;
        return this;
    }

    public RpcConsumer setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes <= 0 ? RpcConstants.DEFAULT_RETRY_TIMES : retryTimes;
        return this;
    }

    public RpcConsumer setFlowPostProcessor(String flowType){
        if (StringUtils.isEmpty(flowType)){
            flowType = RpcConstants.FLOW_POST_PROCESSOR_PRINT;
        }
        this.flowPostProcessor = ExtensionLoader.getExtension(FlowPostProcessor.class, flowType);
        return this;
    }

    public RpcConsumer setExceptionPostProcessor(String exceptionPostProcessorType) {
        if (StringUtils.isEmpty(exceptionPostProcessorType)){
            exceptionPostProcessorType = RpcConstants.EXCEPTION_POST_PROCESSOR_PRINT;
        }
        this.exceptionPostProcessor = ExtensionLoader.getExtension(ExceptionPostProcessor.class, exceptionPostProcessorType);
        return this;
    }

    public ExceptionPostProcessor getExceptionPostProcessor(){
        return this.exceptionPostProcessor;
    }


    public RpcConsumer buildNettyGroup(){
        try {
            bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                    .handler(new RpcConsumerInitializer(heartbeatInterval, enableBuffer, bufferSize, concurrentThreadPool, flowPostProcessor, exceptionPostProcessor));
        }catch (IllegalStateException e){
            bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                    .handler(new RpcConsumerInitializer(heartbeatInterval, enableBuffer, bufferSize, concurrentThreadPool, flowPostProcessor, exceptionPostProcessor));
        }
        return this;
    }



    private void startHeartbeat() {
        executorService = Executors.newScheduledThreadPool(2);
        //扫描并处理所有不活跃的连接
        executorService.scheduleAtFixedRate(() -> {
            logger.info("=============scanNotActiveChannel============");
            ConsumerConnectionManager.scanNotActiveChannel();
        }, 10, scanNotActiveChannelInterval, TimeUnit.MILLISECONDS);

        executorService.scheduleAtFixedRate(()->{
            logger.info("=============broadcastPingMessageFromConsumer============");
            ConsumerConnectionManager.broadcastPingMessageFromConsumer();
        }, 3, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    public static void updateRpcFusingInvokerMap(Integer key, FusingInvoker fusingInvoker) {
        fusingInvokerMap.put(key, fusingInvoker);
    }

    public static FusingInvoker getFusingInvoker(Integer key) {
        return fusingInvokerMap.get(key);
    }

    public static RpcConsumer getInstance(ConsumerConfig consumerConfig){
        if (instance == null){
            synchronized (RpcConsumer.class){
                if (instance == null){
                    instance = new RpcConsumer(consumerConfig);
                }
            }
        }
        return instance;
    }

    public void close(){
        RpcConsumerHandlerHelper.closeRpcClientHandler();
        eventLoopGroup.shutdownGracefully();
        concurrentThreadPool.stop();
        executorService.shutdown();
    }
}
