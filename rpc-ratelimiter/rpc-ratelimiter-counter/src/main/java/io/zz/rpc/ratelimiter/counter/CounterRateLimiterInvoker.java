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
package io.zz.rpc.ratelimiter.counter;

import io.zz.rpc.ratelimiter.base.AbstractRateLimiterInvoker;
import io.zz.rpc.spi.annotation.SPIClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;

@SPIClass
public class CounterRateLimiterInvoker extends AbstractRateLimiterInvoker {

    private final Logger logger = LoggerFactory.getLogger(CounterRateLimiterInvoker.class);

    private final ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();

    @Override
    public boolean tryAcquire() {
        logger.info("execute counter rate limiter...");

        long now = System.currentTimeMillis();

        synchronized (queue) {
            if (queue.size() < permits) {
                queue.offer(now);
                return true;
            }

            // 将队列中所有早于窗口起始时间的请求移除
            while (!queue.isEmpty() && now - queue.peek() >= milliSeconds) {
                queue.poll();
            }

            // 如果队列中请求数量超过最大允许数量，则拒绝该请求
            if (queue.size() >= permits) {
                return false;
            }

            queue.offer(now);    // 将当前请求时间戳加入队列
        }
        return true;
    }

    @Override
    public void release() {
        //TODO ignore
    }
}
