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
package io.zz.rpc.fusing.counter;

import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.fusing.base.AbstractFusingInvoker;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SPIClass
public class CounterFusingInvoker extends AbstractFusingInvoker {

    private final Logger logger = LoggerFactory.getLogger(CounterFusingInvoker.class);

    @Override
    public boolean invokeFusingStrategy() {
        boolean result = false;
        switch (fusingStatus.get()){
            //关闭状态
            case RpcConstants.FUSING_STATUS_CLOSED:
                break;
            //半开启状态
            case RpcConstants.FUSING_STATUS_HALF_OPEN:
                result = true;
                break;
            //开启状态
            case RpcConstants.FUSING_STATUS_OPEN:
                result =  this.invokeOpenFusingStrategy();
                break;
            default:
                break;
        }
        logger.info("execute percent fusing strategy, current fusing status is {}", fusingStatus.get());
        return result;
    }

    @Override
    public double getFailureStrategyValue() {
        long now = System.currentTimeMillis();

        synchronized (queue) {
            if (queue.size() <= totalFailure) {
                return queue.size();
            }
            // 将队列中所有早于窗口起始时间的请求移除
            while (!queue.isEmpty() && now - queue.peek() >= milliSeconds) {
                queue.poll();
            }

            return queue.size();
        }
    }
}
