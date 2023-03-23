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
package io.zz.rpc.spring.boot.consumer.starter;

import io.zz.rpc.common.utils.StringUtils;
import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.consumer.common.RpcClient;
import io.zz.rpc.consumer.spring.RpcReferenceBean;
import io.zz.rpc.consumer.spring.context.RpcConsumerSpringContext;
import io.zz.rpc.consumer.common.config.ConsumerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties
public class SpringBootConsumerAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "rpc.zz.consumer")
    public ConsumerConfig ConsumerConfig(){
        return new ConsumerConfig();
    }

    @Bean
    public List<RpcClient> rpcClient(final ConsumerConfig consumerConfig){
        return parseRpcClient(consumerConfig);
    }

    private List<RpcClient> parseRpcClient(final ConsumerConfig consumerConfig){
        List<RpcClient> rpcClientList = new ArrayList<>();
        ApplicationContext context = RpcConsumerSpringContext.getInstance().getContext();
        Map<String, RpcReferenceBean> rpcReferenceBeanMap = context.getBeansOfType(RpcReferenceBean.class);
        Collection<RpcReferenceBean> rpcReferenceBeans = rpcReferenceBeanMap.values();
        for (RpcReferenceBean rpcReferenceBean : rpcReferenceBeans){
            rpcReferenceBean = this.getRpcReferenceBean(rpcReferenceBean, consumerConfig);
            rpcReferenceBean.init(consumerConfig);
            rpcClientList.add(rpcReferenceBean.getRpcClient());
        }
        return rpcClientList;
    }

    /**
     * 首先从Spring IOC容器中获取RpcReferenceBean，
     * 如果存在RpcReferenceBean，部分RpcReferenceBean的字段为空，则使用springBootConsumerConfig字段进行填充
     * 如果不存在RpcReferenceBean，则使用springBootConsumerConfig构建RpcReferenceBean
     */
    private RpcReferenceBean getRpcReferenceBean(final RpcReferenceBean referenceBean, final ConsumerConfig consumerConfig){
        if (StringUtils.isEmpty(referenceBean.getGroup())
                || (RpcConstants.RPC_COMMON_DEFAULT_GROUP.equals(referenceBean.getGroup()) && !StringUtils.isEmpty(consumerConfig.getGroup()))){
            referenceBean.setGroup(consumerConfig.getGroup());
        }
        if (StringUtils.isEmpty(referenceBean.getVersion())
                || (RpcConstants.RPC_COMMON_DEFAULT_VERSION.equals(referenceBean.getVersion()) && !StringUtils.isEmpty(consumerConfig.getVersion()))){
            referenceBean.setVersion(consumerConfig.getVersion());
        }
        if (StringUtils.isEmpty(referenceBean.getSerializationType())
                || (RpcConstants.RPC_REFERENCE_DEFAULT_SERIALIZATIONTYPE.equals(referenceBean.getSerializationType()) && !StringUtils.isEmpty(consumerConfig.getSerializationType()))){
            referenceBean.setSerializationType(consumerConfig.getSerializationType());
        }

        if (referenceBean.getTimeout() <= 0
                || (RpcConstants.RPC_REFERENCE_DEFAULT_TIMEOUT == referenceBean.getTimeout() && consumerConfig.getTimeout() > 0)){
            referenceBean.setTimeout(consumerConfig.getTimeout());
        }

        if (StringUtils.isEmpty(referenceBean.getProxy())
                || (RpcConstants.RPC_REFERENCE_DEFAULT_PROXY.equals(referenceBean.getProxy()) && !StringUtils.isEmpty(consumerConfig.getProxy()) )){
            referenceBean.setProxy(consumerConfig.getProxy());
        }

        if (!referenceBean.isEnableDirectServer()){
            referenceBean.setEnableDirectServer(consumerConfig.getEnableDirectServer());
        }

        if (StringUtils.isEmpty(referenceBean.getDirectServerUrl())
                || (RpcConstants.RPC_COMMON_DEFAULT_DIRECT_SERVER.equals(referenceBean.getDirectServerUrl()) && !StringUtils.isEmpty(consumerConfig.getDirectServerUrl()))){
            referenceBean.setDirectServerUrl(consumerConfig.getDirectServerUrl());
        }

        if (!referenceBean.isEnableDelayConnection()){
            referenceBean.setEnableDelayConnection(consumerConfig.getEnableDelayConnection());
        }

        if (StringUtils.isEmpty(referenceBean.getReflectType())
                || (RpcConstants.DEFAULT_REFLECT_TYPE.equals(referenceBean.getReflectType()) && !StringUtils.isEmpty(consumerConfig.getReflectType()))){
            referenceBean.setReflectType(consumerConfig.getReflectType());
        }

        if (StringUtils.isEmpty(referenceBean.getFallbackClassName())
                || (RpcConstants.DEFAULT_FALLBACK_CLASS_NAME.equals(referenceBean.getFallbackClassName()) && !StringUtils.isEmpty(consumerConfig.getFallbackClassName()))){
            referenceBean.setFallbackClassName(consumerConfig.getFallbackClassName());
        }

        if (StringUtils.isEmpty(referenceBean.getLoadBalanceType())
                || (RpcConstants.RPC_REFERENCE_DEFAULT_LOADBALANCETYPE.equals(referenceBean.getLoadBalanceType()) && !StringUtils.isEmpty(consumerConfig.getLoadBalanceType()))){
            referenceBean.setLoadBalanceType(consumerConfig.getLoadBalanceType());
        }

        if (referenceBean.getDefaultFallbackTime() <= 0
                || (RpcConstants.DEFAULT_FALLBACK_TIME==referenceBean.getDefaultFallbackTime()) && consumerConfig.getDefaultFallbackTime()>0){
            referenceBean.setDefaultFallbackTime(consumerConfig.getDefaultFallbackTime());
        }
        return referenceBean;
    }
}
