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
package io.zz.rpc.consumer.common.handler;

import com.alibaba.fastjson.JSONObject;
import io.zz.rpc.buffer.cache.BufferCacheManager;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.consumer.common.cache.ConsumerChannelCache;
import io.zz.rpc.exception.processor.ExceptionPostProcessor;
import io.zz.rpc.protocol.RpcProtocol;
import io.zz.rpc.protocol.enumeration.RpcStatus;
import io.zz.rpc.protocol.enumeration.RpcType;
import io.zz.rpc.protocol.header.RpcHeader;
import io.zz.rpc.protocol.header.RpcHeaderFactory;
import io.zz.rpc.protocol.request.RpcRequest;
import io.zz.rpc.protocol.response.RpcResponse;
import proxy.api.future.RPCFuture;
import io.zz.rpc.threadpool.BufferCacheThreadPool;
import io.zz.rpc.threadpool.ConcurrentThreadPool;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcConsumerHandler extends SimpleChannelInboundHandler<RpcProtocol<RpcResponse>> {
    private final Logger logger = LoggerFactory.getLogger(RpcConsumerHandler.class);
    private volatile Channel channel;
    private SocketAddress remotePeer;

    //存储请求ID与RpcResponse协议的映射关系
    private static final Map<Long, RPCFuture> pendingRPC = new ConcurrentHashMap<>();

    private ConcurrentThreadPool concurrentThreadPool;

    /**
     * 是否开启缓冲区
     */
    private boolean enableBuffer;

    /**
     * 缓冲区管理器
     */
    private BufferCacheManager<RpcProtocol<RpcResponse>> bufferCacheManager;

    /**
     * 异常后置处理器
     */
    private ExceptionPostProcessor exceptionPostProcessor;

    public RpcConsumerHandler(boolean enableBuffer, int bufferSize, ConcurrentThreadPool concurrentThreadPool, ExceptionPostProcessor exceptionPostProcessor){
        this.concurrentThreadPool = concurrentThreadPool;
        this.exceptionPostProcessor = exceptionPostProcessor;
        this.enableBuffer = enableBuffer;
        if (enableBuffer){
            this.bufferCacheManager = BufferCacheManager.getInstance(bufferSize);
            BufferCacheThreadPool.submit(() -> {
                consumerBufferCache();
            });
        }
    }

    /**
     * 消费缓冲区数据
     */
    private void consumerBufferCache() {
        //不断消息缓冲区的数据
        while (true){
            RpcProtocol<RpcResponse> protocol = this.bufferCacheManager.take();
            if (protocol != null){
                this.handlerResponseMessage(protocol);
            }
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public SocketAddress getRemotePeer() {
        return remotePeer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.remotePeer = this.channel.remoteAddress();
        ConsumerChannelCache.add(channel);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            //发送一次心跳数据
            RpcHeader header = RpcHeaderFactory.getRequestHeader(RpcConstants.SERIALIZATION_PROTOSTUFF, RpcType.HEARTBEAT_FROM_CONSUMER.getType());
            RpcProtocol<RpcRequest> requestRpcProtocol = new RpcProtocol<RpcRequest>();
            RpcRequest rpcRequest = new RpcRequest();
            rpcRequest.setParameters(new Object[]{RpcConstants.HEARTBEAT_PING});
            requestRpcProtocol.setHeader(header);
            requestRpcProtocol.setBody(rpcRequest);
            ctx.writeAndFlush(requestRpcProtocol);
        }else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        ConsumerChannelCache.remove(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ConsumerChannelCache.remove(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol<RpcResponse> protocol) throws Exception {
        if (protocol == null){
            return;
        }
        concurrentThreadPool.submit(() -> {
            this.handlerMessage(protocol, ctx.channel());
        });
    }

    private void handlerMessage(RpcProtocol<RpcResponse> protocol, Channel channel){
        RpcHeader header = protocol.getHeader();
        //服务提供者响应的心跳消息
        if (header.getMsgType() == (byte) RpcType.HEARTBEAT_TO_CONSUMER.getType()){
            this.handlerHeartbeatMessageToConsumer(protocol, channel);
        }else if (header.getMsgType() == (byte) RpcType.HEARTBEAT_FROM_PROVIDER.getType()){
            this.handlerHeartbeatMessageFromProvider(protocol, channel);
        }else if (header.getMsgType() == (byte) RpcType.RESPONSE.getType()){ //响应消息
            this.handlerResponseMessageOrBuffer(protocol);
        }
    }

    /**
     * 包含是否开启了缓冲区的响应消息
     */
    private void handlerResponseMessageOrBuffer(RpcProtocol<RpcResponse> protocol){
        if (enableBuffer){
            logger.info("enable buffer...");
            this.bufferCacheManager.put(protocol);
        }else {
            this.handlerResponseMessage(protocol);
        }
    }

    /**
     * 处理从服务提供者发送过来的心跳消息
     */
    private void handlerHeartbeatMessageFromProvider(RpcProtocol<RpcResponse> protocol, Channel channel) {
        RpcHeader header = protocol.getHeader();
        header.setMsgType((byte) RpcType.HEARTBEAT_TO_PROVIDER.getType());
        RpcProtocol<RpcRequest> requestRpcProtocol = new RpcProtocol<RpcRequest>();
        RpcRequest request = new RpcRequest();
        request.setParameters(new Object[]{RpcConstants.HEARTBEAT_PONG});
        header.setStatus((byte) RpcStatus.SUCCESS.getCode());
        requestRpcProtocol.setHeader(header);
        requestRpcProtocol.setBody(request);
        channel.writeAndFlush(requestRpcProtocol);
    }

    /**
     * 处理心跳消息
     */
    private void handlerHeartbeatMessageToConsumer(RpcProtocol<RpcResponse> protocol, Channel channel) {
        //此处简单打印即可,实际场景可不做处理
        logger.info("receive service provider heartbeat message, the provider is: {}, the heartbeat message is: {}", channel.remoteAddress(), protocol.getBody().getResult());
    }

    /**
     * 处理响应消息
     */
    private void handlerResponseMessage(RpcProtocol<RpcResponse> protocol) {
        long requestId = protocol.getHeader().getRequestId();
        RPCFuture rpcFuture = pendingRPC.remove(requestId);
        if (rpcFuture != null){
            rpcFuture.done(protocol);
        }
    }

    /**
     * 服务消费者向服务提供者发送请求
     */
    public RPCFuture sendRequest(RpcProtocol<RpcRequest> protocol, boolean oneway){
        logger.info("服务消费者发送的数据===>>>{}", JSONObject.toJSONString(protocol));
        return concurrentThreadPool.submit(() -> {
            return oneway ? this.sendRequestOneway(protocol) : this.sendRequestSync(protocol);
        });
    }


    private RPCFuture sendRequestSync(RpcProtocol<RpcRequest> protocol) {
        RPCFuture rpcFuture = this.getRpcFuture(protocol);
        channel.writeAndFlush(protocol);
        return rpcFuture;
    }


    private RPCFuture sendRequestOneway(RpcProtocol<RpcRequest> protocol) {
        channel.writeAndFlush(protocol);
        return null;
    }


    private RPCFuture getRpcFuture(RpcProtocol<RpcRequest> protocol) {
        RPCFuture rpcFuture = new RPCFuture(protocol, concurrentThreadPool);
        RpcHeader header = protocol.getHeader();
        long requestId = header.getRequestId();
        pendingRPC.put(requestId, rpcFuture);
        return rpcFuture;
    }

    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        ConsumerChannelCache.remove(channel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        exceptionPostProcessor.postExceptionProcessor(cause);
        super.exceptionCaught(ctx, cause);
    }
}
