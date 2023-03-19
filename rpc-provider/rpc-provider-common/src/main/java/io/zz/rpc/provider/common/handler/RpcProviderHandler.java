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
package io.zz.rpc.provider.common.handler;

import io.zz.rpc.annotation.RpcServiceMethod;
import io.zz.rpc.buffer.cache.BufferCacheManager;
import io.zz.rpc.buffer.object.BufferObject;
import io.zz.rpc.cache.result.CacheResultKey;
import io.zz.rpc.cache.result.CacheResultManager;
import io.zz.rpc.common.helper.RpcServiceHelper;
import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.connection.manager.ConnectionManager;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.exception.processor.ExceptionPostProcessor;
import io.zz.rpc.fusing.api.FusingInvoker;
import io.zz.rpc.protocol.RpcProtocol;
import io.zz.rpc.protocol.enumeration.RpcStatus;
import io.zz.rpc.protocol.enumeration.RpcType;
import io.zz.rpc.protocol.header.RpcHeader;
import io.zz.rpc.protocol.request.RpcRequest;
import io.zz.rpc.protocol.response.RpcResponse;
import io.zz.rpc.provider.common.cache.ProviderChannelCache;
import io.zz.rpc.provider.common.server.base.BaseServer;
import io.zz.rpc.ratelimiter.api.RateLimiterInvoker;
import io.zz.rpc.reflect.api.ReflectInvoker;
import io.zz.rpc.spi.loader.ExtensionLoader;
import io.zz.rpc.threadpool.BufferCacheThreadPool;
import io.zz.rpc.threadpool.ConcurrentThreadPool;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RpcProviderHandler extends SimpleChannelInboundHandler<RpcProtocol<RpcRequest>> {
    private final Logger logger = LoggerFactory.getLogger(RpcProviderHandler.class);
    /**
     * 存储服务提供者中被@RpcService注解标注的类的对象
     * key为：serviceName#serviceVersion#group
     * value为：@RpcService注解标注的类的对象
     */
    private final Map<String, Object> handlerMap;

    /**
     * 反射调用真实方法的SPI接口
     */
    private ReflectInvoker reflectInvoker;

    /**
     * 结果缓存管理器
     */
    private CacheResultManager<RpcProtocol<RpcResponse>> cacheResultManager;

    /**
     * 线程池
     */
    private final ConcurrentThreadPool concurrentThreadPool;

    /**
     * 连接管理器
     */
    private ConnectionManager connectionManager;

    /**
     * 是否开启缓冲区
     */
    private boolean enableBuffer;

    /**
     * 缓冲区大小
     */
    private int bufferSize;

    /**
     * 缓冲区管理器
     */
    private BufferCacheManager<BufferObject<RpcRequest>> bufferCacheManager;


    /**
     * 异常处理后置处理器
     */
    private ExceptionPostProcessor exceptionPostProcessor;

    public RpcProviderHandler(String reflectType, boolean enableBuffer, int bufferSize, String exceptionPostProcessorType, Map<String, Object> handlerMap){
        this.handlerMap = handlerMap;
        this.reflectInvoker = ExtensionLoader.getExtension(ReflectInvoker.class, reflectType);

        this.cacheResultManager = CacheResultManager.getInstance(RpcConstants.RPC_SCAN_RESULT_CACHE_EXPIRE);

        this.concurrentThreadPool = ConcurrentThreadPool.getInstance(RpcConstants.DEFAULT_CORE_POOL_SIZE, RpcConstants.DEFAULT_MAXI_NUM_POOL_SIZE);
        this.connectionManager = ConnectionManager.getInstance(Integer.MAX_VALUE, RpcConstants.RPC_CONNECTION_DISUSE_STRATEGY_DEFAULT);

        this.enableBuffer = enableBuffer;
        this.initBuffer(bufferSize);

        if (StringUtils.isEmpty(exceptionPostProcessorType)){
            exceptionPostProcessorType = RpcConstants.EXCEPTION_POST_PROCESSOR_PRINT;
        }
        this.exceptionPostProcessor = ExtensionLoader.getExtension(ExceptionPostProcessor.class, exceptionPostProcessorType);
    }

    /**
     * 初始化缓冲区数据
     */
    private void initBuffer(int bufferSize){
        //开启缓冲
        if (enableBuffer){
            logger.info("enable buffer...");
            bufferCacheManager = BufferCacheManager.getInstance(bufferSize);
            BufferCacheThreadPool.submit(() -> {
                consumerBufferCache();
            });
        }
    }

    /**
     * 消费缓冲区的数据
     */
    private void consumerBufferCache(){
        //不断消息缓冲区的数据
        while (true){
            BufferObject<RpcRequest> bufferObject = this.bufferCacheManager.take();
            if (bufferObject != null){
                ChannelHandlerContext ctx = bufferObject.getCtx();
                RpcProtocol<RpcRequest> protocol = bufferObject.getProtocol();
                RpcHeader header = protocol.getHeader();
                RpcProtocol<RpcResponse> responseRpcProtocol = handlerRequestMessageWithCache(protocol, header);
                this.writeAndFlush(header.getRequestId(), ctx, responseRpcProtocol);
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ProviderChannelCache.add(ctx.channel());
        connectionManager.add(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProviderChannelCache.remove(ctx.channel());
        connectionManager.remove(ctx.channel());
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        ProviderChannelCache.remove(ctx.channel());
        connectionManager.remove(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //如果是IdleStateEvent事件
        if (evt instanceof IdleStateEvent){
            Channel channel = ctx.channel();
            try{
                logger.info("IdleStateEvent triggered, close channel " + channel.remoteAddress());
                connectionManager.remove(channel);
                channel.close();
            }finally {
                channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol<RpcRequest> protocol) throws Exception {
        concurrentThreadPool.submit(() -> {
            connectionManager.update(ctx.channel());

            Integer hashCode = null;
            RpcHeader header = protocol.getHeader();
            if (header.getMsgType() == (byte) RpcType.REQUEST.getType()){
                hashCode = getHashCode(protocol);
            }

            if (enableBuffer){  //开启队列缓冲
                this.bufferRequest(ctx, protocol);
            }else{  //未开启队列缓冲
                this.submitRequest(ctx, protocol, hashCode);
            }
        });
   }

    /**
     * 缓冲数据
     */
    private void bufferRequest(ChannelHandlerContext ctx, RpcProtocol<RpcRequest> protocol) {
        RpcHeader header = protocol.getHeader();
        //接收到服务消费者发送的心跳消息
        if (header.getMsgType() == (byte) RpcType.HEARTBEAT_FROM_CONSUMER.getType()){
            RpcProtocol<RpcResponse> responseRpcProtocol = handlerHeartbeatMessageFromConsumer(protocol, header);
            this.writeAndFlush(protocol.getHeader().getRequestId(), ctx, responseRpcProtocol);
        }else if (header.getMsgType() == (byte) RpcType.HEARTBEAT_TO_PROVIDER.getType()){  //接收到服务消费者响应的心跳消息
            handlerHeartbeatMessageToProvider(protocol, ctx.channel());
        }else if (header.getMsgType() == (byte) RpcType.REQUEST.getType()){ //请求消息
            this.bufferCacheManager.put(new BufferObject<>(ctx, protocol));
        }
    }


    /**
     * 提交请求
     */
    private void submitRequest(ChannelHandlerContext ctx, RpcProtocol<RpcRequest> protocol, Integer hashCode) {
        RpcProtocol<RpcResponse> responseRpcProtocol = handlerMessage(protocol, ctx.channel(), hashCode);
        writeAndFlush(protocol.getHeader().getRequestId(), ctx, responseRpcProtocol);
    }

    /**
     * 向服务消费者写回数据
     */
    private void writeAndFlush(long requestId, ChannelHandlerContext ctx,  RpcProtocol<RpcResponse> responseRpcProtocol){
        ctx.writeAndFlush(responseRpcProtocol).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                logger.debug("Send response for request " + requestId);
            }
        });
    }


    /**
     * 处理消息
     */
    private RpcProtocol<RpcResponse> handlerMessage(RpcProtocol<RpcRequest> protocol, Channel channel, Integer hashCode){
        RpcProtocol<RpcResponse> responseRpcProtocol = null;
        RpcHeader header = protocol.getHeader();
        //接收到服务消费者发送的心跳消息
        if (header.getMsgType() == (byte) RpcType.HEARTBEAT_FROM_CONSUMER.getType()){
            responseRpcProtocol = handlerHeartbeatMessageFromConsumer(protocol, header);
        }else if (header.getMsgType() == (byte) RpcType.HEARTBEAT_TO_PROVIDER.getType()){  //接收到服务消费者响应的心跳消息
            handlerHeartbeatMessageToProvider(protocol, channel);
        }else if (header.getMsgType() == (byte) RpcType.REQUEST.getType()){ //请求消息
            responseRpcProtocol = handlerRequestMessageWithCacheAndRateLimiter(protocol, header, hashCode);
        }
        return responseRpcProtocol;
    }

    /**
     * 带有限流模式提交请求信息
     */
    private RpcProtocol<RpcResponse> handlerRequestMessageWithCacheAndRateLimiter(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode){
        //默认值
        boolean enableRateLimiter = false;
        RateLimiterInvoker rateLimiterInvoker = null;
        if (hashCode != null) {
            enableRateLimiter = BaseServer.getAnnotation(hashCode).enableRateLimiter();
            rateLimiterInvoker = BaseServer.getRateLimiterInvoker(hashCode);
        }
        RpcProtocol<RpcResponse> responseRpcProtocol = null;
        if (enableRateLimiter){
            if (rateLimiterInvoker.tryAcquire()){
                try{
                    responseRpcProtocol = this.handlerRequestMessageWithCacheHashCode(protocol, header, hashCode);
                }finally {
                    rateLimiterInvoker.release();
                }
            }else {
                responseRpcProtocol = this.invokeFailRateLimiterMethod(protocol, header, hashCode);
            }
        }else {
            responseRpcProtocol = this.handlerRequestMessageWithCacheHashCode(protocol, header, hashCode);
        }
        return responseRpcProtocol;
    }


    /**
     * 执行限流失败时的处理逻辑
     */
    private RpcProtocol<RpcResponse> invokeFailRateLimiterMethod(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode) {
        String rateLimiterFailStrategy = "";
        if (hashCode != null) {
            rateLimiterFailStrategy = BaseServer.getAnnotation(hashCode).rateLimiterFailStrategy();
        }
        logger.info("execute {} fail rate limiter strategy...", rateLimiterFailStrategy);
        switch (rateLimiterFailStrategy){
            case RpcConstants.RATE_LIMILTER_FAIL_STRATEGY_EXCEPTION:
            case RpcConstants.RATE_LIMILTER_FAIL_STRATEGY_FALLBACK:
                return this.handlerFallbackMessage(protocol);
            case RpcConstants.RATE_LIMILTER_FAIL_STRATEGY_DIRECT:
                return this.handlerRequestMessageWithCacheHashCode(protocol, header, hashCode);
        }
        return this.handlerRequestMessageWithCacheHashCode(protocol, header, hashCode);
    }

    /**
     * 处理降级（容错）消息
     */
    private RpcProtocol<RpcResponse> handlerFallbackMessage(RpcProtocol<RpcRequest> protocol) {
        RpcProtocol<RpcResponse> responseRpcProtocol = new RpcProtocol<>();
        RpcHeader header = protocol.getHeader();
        header.setStatus((byte)RpcStatus.FALLBACK.getCode());
        responseRpcProtocol.setHeader(header);

        RpcResponse response = new RpcResponse();
        response.setError("provider execute ratelimiter fallback strategy...");
        responseRpcProtocol.setBody(response);

        return responseRpcProtocol;
    }

    /**
     * 结合缓存处理结果
     */
    private RpcProtocol<RpcResponse> handlerRequestMessageWithCache(RpcProtocol<RpcRequest> protocol, RpcHeader header){
        return handlerRequestMessageWithCacheHashCode(protocol, header, getHashCode(protocol));
    }

    /**
     * 结合缓存处理结果
     */
    private RpcProtocol<RpcResponse> handlerRequestMessageWithCacheHashCode(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode){
        //默认值
        boolean enableResultCache = false;
        if (hashCode != null) {
            enableResultCache = BaseServer.getAnnotation(hashCode).enableResultCache();
        }
        header.setMsgType((byte) RpcType.RESPONSE.getType());
        if (enableResultCache) return handlerRequestMessageCache(protocol, header, hashCode);
        return handlerRequestMessageWithFusing(protocol, header, hashCode);
    }

    /**
     * 处理缓存
     */
    private RpcProtocol<RpcResponse> handlerRequestMessageCache(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode) {
        RpcRequest request = protocol.getBody();
        CacheResultKey cacheKey = new CacheResultKey(request.getClassName(), request.getMethodName(), request.getParameterTypes(), request.getParameters(), request.getVersion(), request.getGroup());
        RpcProtocol<RpcResponse> responseRpcProtocol = cacheResultManager.get(cacheKey);
        if (responseRpcProtocol == null){
            responseRpcProtocol = handlerRequestMessageWithFusing(protocol, header, hashCode);
            //设置保存的时间
            cacheKey.setCacheTimeStamp(System.currentTimeMillis());
            cacheResultManager.put(cacheKey, responseRpcProtocol);
        }
        RpcHeader responseHeader = responseRpcProtocol.getHeader();
        responseHeader.setRequestId(header.getRequestId());
        responseRpcProtocol.setHeader(responseHeader);
        return responseRpcProtocol;
    }


    /**
     * 处理服务消费者响应的心跳消息
     */
    private void handlerHeartbeatMessageToProvider(RpcProtocol<RpcRequest> protocol, Channel channel) {
        logger.info("receive service consumer heartbeat message, the consumer is: {}, the heartbeat message is: {}", channel.remoteAddress(), protocol.getBody().getParameters()[0]);
    }

    /**
     * 处理心跳消息
     */
    private RpcProtocol<RpcResponse> handlerHeartbeatMessageFromConsumer(RpcProtocol<RpcRequest> protocol, RpcHeader header) {
        header.setMsgType((byte) RpcType.HEARTBEAT_TO_CONSUMER.getType());
        RpcRequest request = protocol.getBody();
        RpcProtocol<RpcResponse> responseRpcProtocol = new RpcProtocol<RpcResponse>();
        RpcResponse response = new RpcResponse();
        response.setResult(RpcConstants.HEARTBEAT_PONG);
//        response.setAsync(request.getAsync());
        response.setOneway(request.getOneway());
        header.setStatus((byte) RpcStatus.SUCCESS.getCode());
        responseRpcProtocol.setHeader(header);
        responseRpcProtocol.setBody(response);
        return responseRpcProtocol;
    }

    /**
     * 结合服务熔断请求方法
     */
    private RpcProtocol<RpcResponse> handlerRequestMessageWithFusing(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode){
        //默认值
        boolean enableFusing = false;
        if (hashCode != null) {
            enableFusing = BaseServer.getAnnotation(hashCode).enableFusing();
        }
        if (enableFusing){
            return handlerFusingRequestMessage(protocol, header, hashCode);
        }else {
            return handlerRequestMessage(protocol, header);
        }
    }


    /**
     * 开启熔断策略时调用的方法
     */
    private RpcProtocol<RpcResponse> handlerFusingRequestMessage(RpcProtocol<RpcRequest> protocol, RpcHeader header, Integer hashCode){
        FusingInvoker fusingInvoker = null;
        if (hashCode != null) {
            fusingInvoker = BaseServer.getFusingInvoker(hashCode);
        }
        //如果触发了熔断的规则，则直接返回降级处理数据
        if (fusingInvoker.invokeFusingStrategy()){
            return handlerFallbackMessage(protocol);
        }
        //请求计数加1
        fusingInvoker.incrementCount();

        //调用handlerRequestMessage()方法获取数据
        RpcProtocol<RpcResponse> responseRpcProtocol = handlerRequestMessage(protocol, header);
        if (responseRpcProtocol == null) return null;
        //如果是调用失败，则失败次数加1
        if (responseRpcProtocol.getHeader().getStatus() == (byte) RpcStatus.FAIL.getCode()){
            fusingInvoker.markFailed();
        }else {
            fusingInvoker.markSuccess();
        }
        return responseRpcProtocol;
    }

    /**
     *
     * 处理请求消息
     */
    private RpcProtocol<RpcResponse> handlerRequestMessage(RpcProtocol<RpcRequest> protocol, RpcHeader header) {
        RpcRequest request = protocol.getBody();
        logger.debug("Receive request " + header.getRequestId());
        RpcProtocol<RpcResponse> responseRpcProtocol = new RpcProtocol<RpcResponse>();
        RpcResponse response = new RpcResponse();
        try {
            Object result = handle(request);
            response.setResult(result);
//            response.setAsync(request.getAsync());
            response.setOneway(request.getOneway());
            header.setStatus((byte) RpcStatus.SUCCESS.getCode());
        } catch (Throwable t) {
            exceptionPostProcessor.postExceptionProcessor(t);
            response.setError(t.toString());
            header.setStatus((byte) RpcStatus.FAIL.getCode());
            logger.error("RPC Server handle request error",t);
        }
        responseRpcProtocol.setHeader(header);
        responseRpcProtocol.setBody(response);
        return responseRpcProtocol;
    }

    private Object handle(RpcRequest request) throws Throwable {
        String serviceKey = RpcServiceHelper.buildServiceKey(request.getClassName(), request.getVersion(), request.getGroup());
        Object serviceBean = handlerMap.get(serviceKey);
        if (serviceBean == null) {
            throw new RuntimeException(String.format("service not exist: %s:%s", request.getClassName(), request.getMethodName()));
        }

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        logger.debug(serviceClass.getName());
        logger.debug(methodName);
        if (parameterTypes != null && parameterTypes.length > 0){
            for (int i = 0; i < parameterTypes.length; ++i) {
                logger.debug(parameterTypes[i].getName());
            }
        }

        if (parameters != null && parameters.length > 0){
            for (int i = 0; i < parameters.length; ++i) {
                logger.debug(parameters[i].toString());
            }
        }
        return this.reflectInvoker.invokeMethod(serviceBean, serviceClass, methodName, parameterTypes, parameters);
    }

    public Integer getHashCode(RpcProtocol<RpcRequest> protocol) {
        RpcRequest request = protocol.getBody();
        String serviceKey = RpcServiceHelper.buildServiceKey(request.getClassName(), request.getVersion(), request.getGroup());
        Object serviceBean = handlerMap.get(serviceKey);
        if (serviceBean == null) {
            return null;
        }

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        RpcServiceMethod rpcServiceMethod;
        try{
            rpcServiceMethod = serviceClass.getMethod(methodName, parameterTypes).getAnnotation(RpcServiceMethod.class);
        }catch (Exception e){
            return null;
        }
        if (rpcServiceMethod != null) {
            CacheResultKey cacheKey = new CacheResultKey(serviceClass.getName(), methodName, parameterTypes, new Object[0], request.getVersion(), request.getGroup());
            BaseServer.updateAnnotation(cacheKey.hashCode(), rpcServiceMethod);
            return cacheKey.hashCode();
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("server caught exception", cause);
        exceptionPostProcessor.postExceptionProcessor(cause);
        ProviderChannelCache.remove(ctx.channel());
        connectionManager.remove(ctx.channel());
        ctx.close();
    }
}