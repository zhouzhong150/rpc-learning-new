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
package io.zz.rpc.provider.common.server.base;

import io.zz.rpc.annotation.RpcServiceMethod;
import io.zz.rpc.cache.result.CacheResultManager;
import io.zz.rpc.codec.RpcDecoder;
import io.zz.rpc.codec.RpcEncoder;
import io.zz.rpc.connection.manager.ConnectionManager;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.flow.processor.FlowPostProcessor;
import io.zz.rpc.fusing.api.FusingInvoker;
import io.zz.rpc.provider.common.handler.RpcProviderHandler;
import io.zz.rpc.provider.common.manager.ProviderConnectionManager;
import io.zz.rpc.provider.common.server.api.Server;
import io.zz.rpc.ratelimiter.api.RateLimiterInvoker;
import io.zz.rpc.registry.api.RegistryService;
import io.zz.rpc.registry.api.config.RegistryConfig;
import io.zz.rpc.spi.loader.ExtensionLoader;
import io.zz.rpc.threadpool.ConcurrentThreadPool;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BaseServer implements Server {

    private final Logger logger = LoggerFactory.getLogger(BaseServer.class);
    //主机域名或者IP地址
    private String host = "127.0.0.1";
    //端口号
    private int port = 27110;
    protected String serverRegistryHost;
    protected int serverRegistryPort;
    //存储的是实体类关系
    protected Map<String, Object> handlerMap = new HashMap<>();
    private String reflectType;

    protected RegistryService registryService;

    //心跳定时任务线程池
    private ScheduledExecutorService executorService;
    //心跳间隔时间，默认30秒
    private int heartbeatInterval = 30000;
    //扫描并移除空闲连接时间，默认60秒
    private int scanNotActiveChannelInterval = 60000;


    private final ConcurrentThreadPool concurrentThreadPool;

    private ConnectionManager connectionManager;

    //结果缓存过期时长，默认5秒
    private int resultCacheExpire = 5000;

    //流控分析后置处理器
    private FlowPostProcessor flowPostProcessor;

    private boolean enableBuffer;

    private int bufferSize;

    //异常后置处理器标识
    private String exceptionPostProcessorType;

    private CacheResultManager cacheResultManager;

    //存储方法对应的限流器
    private static ConcurrentHashMap<Integer, RateLimiterInvoker> rpcRateLimiterInvokerMap;

    //存储方法对应的熔断器
    private static ConcurrentHashMap<Integer, FusingInvoker> fusingInvokerMap;

    //存储方法对应的注解
    private static ConcurrentHashMap<Integer, RpcServiceMethod> annotationMap;

    /**
     *
     boolean enableRateLimiter,
     String rateLimiterType, int permits, int milliSeconds, String rateLimiterFailStrategy,
     */

    public BaseServer(String serverAddress, String serverRegistryAddress, String registryAddress, String registryType,
                      String reflectType, int heartbeatInterval, int scanNotActiveChannelInterval,
                      int resultCacheExpire, int corePoolSize, int maximumPoolSize, String flowType,
                      int maxConnections, String disuseStrategyType, boolean enableBuffer, int bufferSize, String exceptionPostProcessorType){
        if (!StringUtils.isEmpty(serverAddress)){
            String[] serverArray = serverAddress.split(":");
            this.host = serverArray[0];
            this.port = Integer.parseInt(serverArray[1]);
        }
        if (!StringUtils.isEmpty(serverRegistryAddress)){
            String[] serverRegistryAddressArray = serverRegistryAddress.split(":");
            this.serverRegistryHost = serverRegistryAddressArray[0];
            this.serverRegistryPort = Integer.parseInt(serverRegistryAddressArray[1]);
        }else{
            this.serverRegistryHost = this.host;
            this.serverRegistryPort = this.port;
        }
        if (heartbeatInterval > 0){
            this.heartbeatInterval = heartbeatInterval;
        }
        if (scanNotActiveChannelInterval > 0){
            this.scanNotActiveChannelInterval = scanNotActiveChannelInterval;
        }
        this.reflectType = reflectType;
        this.registryService = this.getRegistryService(registryAddress, registryType);

        if (resultCacheExpire > 0){
            this.resultCacheExpire = resultCacheExpire;
        }

        this.concurrentThreadPool = ConcurrentThreadPool.getInstance(corePoolSize, maximumPoolSize);
        this.connectionManager = ConnectionManager.getInstance(maxConnections, disuseStrategyType);
        this.enableBuffer = enableBuffer;
        this.bufferSize = bufferSize;
        this.exceptionPostProcessorType = exceptionPostProcessorType;
        this.flowPostProcessor = ExtensionLoader.getExtension(FlowPostProcessor.class, flowType);
        this.cacheResultManager = CacheResultManager.getInstance(resultCacheExpire);
        rpcRateLimiterInvokerMap = new ConcurrentHashMap<>();
        fusingInvokerMap = new ConcurrentHashMap<>();
        annotationMap = new ConcurrentHashMap<>();
    }

    private RegistryService getRegistryService(String registryAddress, String registryType) {
        //TODO 后续扩展支持SPI
        RegistryService registryService = null;
        try {
            registryService = ExtensionLoader.getExtension(RegistryService.class, registryType);
            registryService.init(new RegistryConfig(registryAddress, registryType));
        }catch (Exception e){
            logger.error("RPC Server init error", e);
        }
        return registryService;
    }

    @Override
    public void startNettyServer() {
        this.startHeartbeat();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>(){
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    .addLast(RpcConstants.CODEC_DECODER, new RpcDecoder(flowPostProcessor))
                                    .addLast(RpcConstants.CODEC_ENCODER, new RpcEncoder(flowPostProcessor))
                                    .addLast(RpcConstants.CODEC_SERVER_IDLE_HANDLER, new IdleStateHandler(0, 0, heartbeatInterval, TimeUnit.MILLISECONDS))
                                    .addLast(RpcConstants.CODEC_HANDLER, new RpcProviderHandler(reflectType, enableBuffer, bufferSize, exceptionPostProcessorType, handlerMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG,128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture future = bootstrap.bind(host, port).sync();
            logger.info("Server started on {}:{}", host, port);
            future.channel().closeFuture().sync();
        }catch (Exception e){
            logger.error("RPC Server start error", e);
        }finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }


    private void startHeartbeat() {
        executorService = Executors.newScheduledThreadPool(2);
        //扫描并处理所有不活跃的连接
        executorService.scheduleAtFixedRate(() -> {
            logger.info("=============scanNotActiveChannel============");
            ProviderConnectionManager.scanNotActiveChannel();
        }, 10, scanNotActiveChannelInterval, TimeUnit.MILLISECONDS);

        executorService.scheduleAtFixedRate(()->{
            logger.info("=============broadcastPingMessageFromProvoder============");
            ProviderConnectionManager.broadcastPingMessageFromProvider();
        }, 3, heartbeatInterval, TimeUnit.MILLISECONDS);
    }


    public static void updateRpcRateLimiterInvokerMap(Integer key, RateLimiterInvoker rateLimiterInvoker) {
        rpcRateLimiterInvokerMap.put(key, rateLimiterInvoker);
    }

    public static RateLimiterInvoker getRateLimiterInvoker(Integer key) {
        return rpcRateLimiterInvokerMap.get(key);
    }

    public static void updateRpcFusingInvokerMap(Integer key, FusingInvoker fusingInvoker) {
        fusingInvokerMap.put(key, fusingInvoker);
    }

    public static FusingInvoker getFusingInvoker(Integer key) {
        return fusingInvokerMap.get(key);
    }

    public static void updateAnnotation(Integer key, RpcServiceMethod rpcServiceMethod) {
        annotationMap.put(key, rpcServiceMethod);
    }

    public static RpcServiceMethod getAnnotation(Integer key) {
        return annotationMap.get(key);
    }
}
