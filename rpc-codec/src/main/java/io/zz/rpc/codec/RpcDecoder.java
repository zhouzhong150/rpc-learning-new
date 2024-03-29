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
package io.zz.rpc.codec;

import io.zz.rpc.common.utils.SerializationUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.flow.processor.FlowPostProcessor;
import io.zz.rpc.protocol.RpcProtocol;
import io.zz.rpc.protocol.enumeration.RpcType;
import io.zz.rpc.protocol.header.RpcHeader;
import io.zz.rpc.protocol.request.RpcRequest;
import io.zz.rpc.protocol.response.RpcResponse;
import io.zz.rpc.serialization.api.Serialization;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

public class RpcDecoder  extends ByteToMessageDecoder implements RpcCodec {

    private FlowPostProcessor postProcessor;
    public RpcDecoder(FlowPostProcessor postProcessor){
        this.postProcessor = postProcessor;
    }

    @Override
    public final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < RpcConstants.HEADER_TOTAL_LEN) {
            return;
        }
        in.markReaderIndex();

        short magic = in.readShort();
        if (magic != RpcConstants.MAGIC) {
            throw new IllegalArgumentException("magic number is illegal, " + magic);
        }

        byte msgType = in.readByte();
        byte status = in.readByte();
        long requestId = in.readLong();

        ByteBuf serializationTypeByteBuf = in.readBytes(SerializationUtils.MAX_SERIALIZATION_TYPE_COUNR);
        String serializationType = SerializationUtils.subString(serializationTypeByteBuf.toString(CharsetUtil.UTF_8));

        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        RpcType msgTypeEnum = RpcType.findByType(msgType);
        if (msgTypeEnum == null) {
            return;
        }


        RpcHeader header = new RpcHeader();
        header.setMagic(magic);
        header.setStatus(status);
        header.setRequestId(requestId);
        header.setMsgType(msgType);
        header.setSerializationType(serializationType);
        header.setMsgLen(dataLength);
        //TODO Serialization是扩展点
        Serialization serialization = getSerialization(serializationType);
        switch (msgTypeEnum) {
            case REQUEST:
            //TODO 新增CASE
            //服务消费者发送给服务提供者的心跳数据
            case HEARTBEAT_FROM_CONSUMER:
            //服务提供者发送给服务消费者的心跳数据
            case HEARTBEAT_TO_PROVIDER:
                RpcRequest request = serialization.deserialize(data, RpcRequest.class);
                if (request != null) {
                    RpcProtocol<RpcRequest> protocol = new RpcProtocol<>();
                    protocol.setHeader(header);
                    protocol.setBody(request);
                    out.add(protocol);
                }
                break;
            case RESPONSE:
            //TODO 新增case
            //服务提供者响应服务消费者的心跳数据
            case HEARTBEAT_TO_CONSUMER:
            //服务消费者响应服务提供者的心跳数据
            case HEARTBEAT_FROM_PROVIDER:
                RpcResponse response = serialization.deserialize(data, RpcResponse.class);
                if (response != null) {
                    RpcProtocol<RpcResponse> protocol = new RpcProtocol<>();
                    protocol.setHeader(header);
                    protocol.setBody(response);
                    out.add(protocol);
                }
                break;
        }
        //异步调用流控分析后置处理器
        this.postFlowProcessor(postProcessor, header);
    }
}
