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
package io.zz.rpc.spring.boot.provider.starter;

import io.zz.rpc.provider.spring.RpcSpringServer;
import io.zz.rpc.spring.boot.provider.config.SpringBootProviderConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
public class SpringBootProviderAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "rpc.zz.provider")
    public SpringBootProviderConfig springBootProviderConfig(){
        return new SpringBootProviderConfig();
    }

    @Bean
    public RpcSpringServer rpcSpringServer(final SpringBootProviderConfig springBootProviderConfig){
        return new RpcSpringServer(springBootProviderConfig.getServerAddress(),
                springBootProviderConfig.getServerRegistryAddress(),
                springBootProviderConfig.getRegistryAddress(),
                springBootProviderConfig.getRegistryType(),
                springBootProviderConfig.getReflectType(),
                springBootProviderConfig.getHeartbeatInterval(),
                springBootProviderConfig.getScanNotActiveChannelInterval(),
                springBootProviderConfig.getResultCacheExpire(),
                springBootProviderConfig.getCorePoolSize(),
                springBootProviderConfig.getMaximumPoolSize(),
                springBootProviderConfig.getFlowType(),
                springBootProviderConfig.getMaxConnections(),
                springBootProviderConfig.getDisuseStrategyType(),
                springBootProviderConfig.getEnableBuffer(),
                springBootProviderConfig.getBufferSize(),
                springBootProviderConfig.getExceptionPostProcessorType());
    }
}
