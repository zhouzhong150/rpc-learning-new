/**
 * Copyright 2022-9999 the original author or authors.
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
package io.zz.rpc.demo.spring.annotation.consumer.service.impl;

import io.zz.rpc.annotation.RpcReference;
import io.zz.rpc.demo.api.DemoService;
import io.zz.rpc.demo.spring.annotation.consumer.hello.FallbackDemoServcieImpl;
import io.zz.rpc.demo.spring.annotation.consumer.service.ConsumerDemoService;
import org.springframework.stereotype.Service;

@Service
public class ConsumerDemoServiceImpl implements ConsumerDemoService {

    @RpcReference(loadBalanceType = "zkconsistenthash", version = "1.0.0", group = "zz", serializationType = "protostuff", proxy = "cglib", timeout = 30000, reflectType = "jdk", fallbackClass = FallbackDemoServcieImpl.class)
    private DemoService demoService;

    @Override
    public String hello(String name) {
        return demoService.hello(name);
    }
}
