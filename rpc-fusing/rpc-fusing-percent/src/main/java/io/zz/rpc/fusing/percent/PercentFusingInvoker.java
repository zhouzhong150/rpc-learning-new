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
package io.zz.rpc.fusing.percent;

import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.fusing.base.AbstractFusingInvoker;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SPIClass
public class PercentFusingInvoker extends AbstractFusingInvoker {
    private final Logger logger = LoggerFactory.getLogger(PercentFusingInvoker.class);

    @Override
    public boolean invokeFusingStrategy() {
        boolean result = false;
        switch (fusingStatus.get()){
            //关闭状态
            case RpcConstants.FUSING_STATUS_CLOSED:
                result =  this.invokeClosedFusingStrategy();
                break;
            //半开启状态
            case RpcConstants.FUSING_STATUS_HALF_OPEN:
                result = this.invokeHalfOpenFusingStrategy();
                break;
            //开启状态
            case RpcConstants.FUSING_STATUS_OPEN:
                result =  this.invokeOpenFusingStrategy();
                break;
            default:
                result = this.invokeClosedFusingStrategy();
                break;
        }
        logger.info("execute percent fusing strategy, current fusing status is {}", fusingStatus.get());
        return result;
    }

    @Override
    public double getFailureStrategyValue() {
        if (currentCounter.get() <= 0) return 0;
        return currentFailureCounter.doubleValue() / currentCounter.doubleValue() * 100;
    }
}