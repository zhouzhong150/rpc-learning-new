///**
// * Copyright 2022-9999 the original author or authors.
// * <p>
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// * <p>
// * http://www.apache.org/licenses/LICENSE-2.0
// * <p>
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package io.zz.demo.consumer;
//
//import io.zz.rpc.consumer.common.RpcClient;
//import io.zz.rpc.demo.api.DemoService;
//import proxy.api.async.IAsyncObjectProxy;
//import proxy.api.future.RPCFuture;
//import org.junit.Before;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class ConsumerNativeDemo {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerNativeDemo.class);
//
//    private RpcClient rpcClient;
//
//    @Before
//    public void initRpcClient(){
//        rpcClient = new RpcClient("127.0.0.1:2181", "zookeeper", "zkconsistenthash","asm","1.0.0", "zz", "protostuff", 3000, false, false, 30000, 60000, 1000, 3, false, 10000, false, "127.0.0.1:27880", true, 16, 16, "print", false, 2, "jdk", "hello.io.zz.demo.consumer.FallbackDemoServcieImpl", false, "counter", 1, 5000, "fallback", true, "percent", 10, 10000, "print");
//    }
//
//
//    @Test
//    public void testInterfaceRpc() throws InterruptedException {
//        DemoService demoService = rpcClient.create(DemoService.class);
//        for (int i = 0; i < 5; i++){
//            String result = demoService.hello("zz");
//            LOGGER.info("返回的结果数据===>>> " + result);
//        }
//        //rpcClient.shutdown();
//        while (true){
//            Thread.sleep(1000);
//        }
//    }
//
//    @Test
//    public void testAsyncInterfaceRpc() throws Exception {
//        IAsyncObjectProxy demoService = rpcClient.createAsync(DemoService.class);
//        RPCFuture future = demoService.call("hello", "zz");
//        LOGGER.info("返回的结果数据===>>> " + future.get());
//        rpcClient.shutdown();
//    }
//}
