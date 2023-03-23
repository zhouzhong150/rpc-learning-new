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

import io.zz.rpc.flow.processor.FlowPostProcessor;
import io.zz.rpc.protocol.header.RpcHeader;
import io.zz.rpc.serialization.api.Serialization;
import io.zz.rpc.spi.loader.ExtensionLoader;
import io.zz.rpc.threadpool.FlowPostProcessorThreadPool;

public interface RpcCodec {

    /**
     * 根据serializationType通过SPI获取序列化句柄
     * @param serializationType 序列化方式
     * @return Serialization对象
     */
    default Serialization getSerialization(String serializationType){
        return ExtensionLoader.getExtension(Serialization.class, serializationType);
    }

    /**
     * 调用RPC框架流量分析后置处理器
     * @param postProcessor 后置处理器
     * @param header 封装了流量信息的消息头
     */
    default void postFlowProcessor(FlowPostProcessor postProcessor, RpcHeader header){
        //异步调用流控分析后置处理器
        FlowPostProcessorThreadPool.submit(() -> {
            postProcessor.postRpcHeaderProcessor(header);
        });
    }
}
