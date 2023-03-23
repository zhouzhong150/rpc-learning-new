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
package io.zz.rpc.annotation;

import io.zz.rpc.config.ConsumerConfig;
import io.zz.rpc.constants.RpcConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Autowired
public @interface RpcReference {

    /**
     * 版本号
     */
    String version() default RpcConstants.RPC_COMMON_DEFAULT_VERSION;

    /**
     * 服务分组，默认为空
     */
    String group() default RpcConstants.RPC_COMMON_DEFAULT_GROUP;

    /**
     * 序列化类型，目前的类型包含：protostuff、kryo、json、jdk、hessian2、fst
     */
    String serializationType() default RpcConstants.RPC_REFERENCE_DEFAULT_SERIALIZATIONTYPE;

    /**
     * 超时时间，默认5s
     */
    long timeout() default RpcConstants.RPC_REFERENCE_DEFAULT_TIMEOUT;

    /**
     * 代理的类型，jdk：jdk代理， javassist: javassist代理, cglib: cglib代理
     */
    String proxy() default RpcConstants.RPC_REFERENCE_DEFAULT_PROXY;

    /**
     * 是否开启直连服务
     */
    boolean enableDirectServer() default false;

    /**
     * 直连服务的地址
     */
    String directServerUrl() default RpcConstants.RPC_COMMON_DEFAULT_DIRECT_SERVER;

    /**
     * 是否开启延迟连接
     */
    boolean enableDelayConnection() default false;

    /**
     * 容错class名称
     */
    String fallbackClassName() default RpcConstants.DEFAULT_FALLBACK_CLASS_NAME;

    /**
     * 容错class
     */
    Class<?> fallbackClass() default void.class;

    /**
     * 反射类型
     */
    String reflectType() default RpcConstants.DEFAULT_REFLECT_TYPE;

    /**
     * 负载均衡类型，默认基于ZK的一致性Hash
     */
    String loadBalanceType() default RpcConstants.RPC_REFERENCE_DEFAULT_LOADBALANCETYPE;

    /**
     * 熔断时间周期
     */
    long defaultFallbackTime() default 30000L;

}
