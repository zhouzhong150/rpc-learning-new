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
package proxy.api.config;

import io.zz.rpc.consumer.common.RpcClient;
import io.zz.rpc.registry.api.RegistryService;

import java.io.Serializable;

public class ProxyConfig<T> implements Serializable {
    private static final long serialVersionUID = 6648940252795742398L;

    /**
     * 接口的Class实例
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
     * 超时时间
     */
    private long timeout;

    private RpcClient rpcClient;

    /**
     * 序列化类型
     */
    private String serializationType;

    /**
     * 反射类型
     */
    private String reflectType;

    /**
     * 容错class名称
     */
    private String fallbackClassName;

    /**
     * 容错class
     */
    private Class<?> fallbackClass;

    /**
     * 异常监控类型
     */
    private String exceptionPostProcessorType;

    public ProxyConfig() {
    }

    public ProxyConfig(Class<T> clazz, String serviceVersion, String serviceGroup, String serializationType, long timeout,
                       RpcClient rpcClient, String reflectType, String fallbackClassName, Class<?> fallbackClass) {
        this.clazz = clazz;
        this.serviceVersion = serviceVersion;
        this.serviceGroup = serviceGroup;
        this.timeout = timeout;
        this.rpcClient = rpcClient;
        this.serializationType = serializationType;
        this.reflectType = reflectType;
        this.fallbackClassName = fallbackClassName;
        this.fallbackClass = fallbackClass;
    }


    public Class<T> getClazz() {
        return clazz;
    }

    public void setClazz(Class<T> clazz) {
        this.clazz = clazz;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public String getServiceGroup() {
        return serviceGroup;
    }

    public void setServiceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public RpcClient getRpcClient() {
        return rpcClient;
    }

    public void setRpcClient(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String getSerializationType() {
        return serializationType;
    }

    public void setSerializationType(String serializationType) {
        this.serializationType = serializationType;
    }

    public String getReflectType() {
        return reflectType;
    }

    public void setReflectType(String reflectType) {
        this.reflectType = reflectType;
    }

    public String getFallbackClassName() {
        return fallbackClassName;
    }

    public void setFallbackClassName(String fallbackClassName) {
        this.fallbackClassName = fallbackClassName;
    }

    public Class<?> getFallbackClass() {
        return fallbackClass;
    }

    public void setFallbackClass(Class<?> fallbackClass) {
        this.fallbackClass = fallbackClass;
    }

    public String getExceptionPostProcessorType() {
        return exceptionPostProcessorType;
    }

    public void setExceptionPostProcessorType(String exceptionPostProcessorType) {
        this.exceptionPostProcessorType = exceptionPostProcessorType;
    }
}
