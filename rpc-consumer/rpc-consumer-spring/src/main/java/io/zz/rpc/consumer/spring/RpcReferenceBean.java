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
package io.zz.rpc.consumer.spring;

import io.zz.rpc.consumer.common.RpcClient;
import io.zz.rpc.consumer.common.config.ConsumerConfig;
import org.springframework.beans.factory.FactoryBean;

public class RpcReferenceBean implements FactoryBean<Object> {
    /**
     * 接口类型
     */
    private Class<?> interfaceClass;
    /**
     * 版本号
     */
    private String version;

    /**
     * 序列化类型：fst/kryo/protostuff/jdk/hessian2/json
     */
    private String serializationType;

    /**
     * 超时时间
     */
    private long timeout;

    /**
     * 服务分组
     */
    private String group;

    /**
     * 代理方式
     */
    private String proxy;

    /**
     * 生成的代理对象
     */
    private Object object;

    private RpcClient rpcClient;

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
     * 反射类型
     */
    private String reflectType;

    /**
     * 容错类Class名称
     */
    private String fallbackClassName;

    /**
     * 容错类
     */
    private Class<?> fallbackClass;

    /**
     * 熔断时间周期（负载均衡）
     */
    private Long defaultFallbackTime;

    /**
     * 负载均衡类型：zkconsistenthash
     */
    private String loadBalanceType;

    @Override
    public Object getObject() throws Exception {
        return object;
    }

    @Override
    public Class<?> getObjectType() {
       return interfaceClass;
    }

    @SuppressWarnings("unchecked")
    public void init(ConsumerConfig consumerConfig){
        rpcClient = new RpcClient(consumerConfig, proxy, version, group, serializationType,
                timeout, enableDirectServer, directServerUrl, enableDelayConnection,
                reflectType, fallbackClassName,
                loadBalanceType, defaultFallbackTime);
        rpcClient.setFallbackClass(fallbackClass);
        this.object = rpcClient.create(interfaceClass);
    }

    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setLoadBalanceType(String loadBalanceType) {
        this.loadBalanceType = loadBalanceType;
    }

    public void setSerializationType(String serializationType) {
        this.serializationType = serializationType;
    }

    public String getVersion() {
        return version;
    }

    public String getLoadBalanceType() {
        return loadBalanceType;
    }

    public String getSerializationType() {
        return serializationType;
    }

    public long getTimeout() {
        return timeout;
    }

    public String getGroup() {
        return group;
    }

    public String getProxy() {
        return proxy;
    }

    public RpcClient getRpcClient() {
        return rpcClient;
    }

    public boolean isEnableDirectServer() {
        return enableDirectServer;
    }

    public void setEnableDirectServer(boolean enableDirectServer) {
        this.enableDirectServer = enableDirectServer;
    }

    public String getDirectServerUrl() {
        return directServerUrl;
    }

    public void setDirectServerUrl(String directServerUrl) {
        this.directServerUrl = directServerUrl;
    }

    public boolean isEnableDelayConnection() {
        return enableDelayConnection;
    }

    public void setEnableDelayConnection(boolean enableDelayConnection) {
        this.enableDelayConnection = enableDelayConnection;
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

    public Long getDefaultFallbackTime() {
        return defaultFallbackTime;
    }

    public void setDefaultFallbackTime(Long defaultFallbackTime) {
        this.defaultFallbackTime = defaultFallbackTime;
    }
}
