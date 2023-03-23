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
package io.zz.rpc.fusing.base;

import io.zz.rpc.constants.RpcConstants;
import io.zz.rpc.fusing.api.FusingInvoker;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractFusingInvoker implements FusingInvoker {

    /**
     * 熔断状态，1：关闭； 2：半开启； 3：开启
     */
    protected static final AtomicInteger fusingStatus = new AtomicInteger(RpcConstants.FUSING_STATUS_CLOSED);

    /**
     * 当前调用次数
     */
    protected final AtomicInteger currentCounter = new AtomicInteger(0);

    /**
     * 当前调用失败的次数
     */
    protected final AtomicInteger currentFailureCounter = new AtomicInteger(0);

    /**
     * 调用失败的请求时间戳队列
     */
    protected final ConcurrentLinkedQueue<Long> queue  = new ConcurrentLinkedQueue<>();

    /**
     * 半开启状态下的等待状态
     */
    protected final AtomicInteger fusingWaitStatus = new AtomicInteger(RpcConstants.FUSING_WAIT_STATUS_INIT);

    /**
     * 熔断时间范围的开始时间点
     */
    protected volatile long lastTimeStamp = System.currentTimeMillis();

    /**
     * 在milliSeconds毫秒内触发熔断操作的上限值
     * 可能是错误个数，也可能是错误率
     */
    protected double totalFailure;

    /**
     * 毫秒数
     */
    protected int milliSeconds;

    /**
     * 获取失败策略的结果值
     */
    public abstract double getFailureStrategyValue();

    /**
     * 重置数量
     */
    protected void resetCount(){
        currentFailureCounter.set(0);
        currentCounter.set(0);
    }

    @Override
    public void incrementCount() {
        currentCounter.incrementAndGet();
    }

    @Override
    public void markSuccess() {
        long currentTimeStamp = System.currentTimeMillis();
        if (fusingStatus.get() == RpcConstants.FUSING_STATUS_HALF_OPEN){
            fusingStatus.set(RpcConstants.FUSING_STATUS_CLOSED);
            fusingWaitStatus.compareAndSet(RpcConstants.FUSING_WAIT_STATUS_WAITINF, RpcConstants.FUSING_WAIT_STATUS_INIT);
            lastTimeStamp = currentTimeStamp;
            this.resetCount();
        }
    }

    @Override
    public void markFailed() {
        long currentTimeStamp = System.currentTimeMillis();

        queue.offer(currentTimeStamp);

        currentFailureCounter.incrementAndGet();
        if (fusingStatus.get() == RpcConstants.FUSING_STATUS_HALF_OPEN){
            fusingStatus.set(RpcConstants.FUSING_STATUS_OPEN);
            fusingWaitStatus.compareAndSet(RpcConstants.FUSING_WAIT_STATUS_WAITINF, RpcConstants.FUSING_WAIT_STATUS_INIT);
            lastTimeStamp = currentTimeStamp;
            this.resetCount();
        } else if (fusingStatus.get() == RpcConstants.FUSING_STATUS_CLOSED) {
            //超过一个指定的时间范围
            if (currentTimeStamp - lastTimeStamp >= milliSeconds){
                lastTimeStamp = currentTimeStamp;
                this.resetCount();
            }
            //如果当前错误数或者百分比大于或等于配置的百分比
            if (this.getFailureStrategyValue() >= totalFailure){
                lastTimeStamp = currentTimeStamp;
                fusingStatus.set(RpcConstants.FUSING_STATUS_OPEN);
            }
        }
    }

    @Override
    public void init(double totalFailure, int milliSeconds) {
        this.totalFailure = totalFailure <= 0 ? RpcConstants.DEFAULT_FUSING_TOTAL_FAILURE : totalFailure;
        this.milliSeconds = milliSeconds <= 0 ? RpcConstants.DEFAULT_FUSING_MILLI_SECONDS : milliSeconds;
    }

    /**
     * 处理开启状态的逻辑
     */
    protected boolean invokeOpenFusingStrategy() {
        //获取当前时间
        long currentTimeStamp = System.currentTimeMillis();
        //超过一个指定的时间范围
        if (currentTimeStamp - lastTimeStamp >= milliSeconds){
            //修改等待状态，让修改成功的线程进入半开启状态
            if (fusingWaitStatus.compareAndSet(RpcConstants.FUSING_WAIT_STATUS_INIT, RpcConstants.FUSING_WAIT_STATUS_WAITINF)){
                fusingStatus.set(RpcConstants.FUSING_STATUS_HALF_OPEN);
                lastTimeStamp = currentTimeStamp;
                this.resetCount();
                return false;
            }
        }
        return true;
    }

    /**
     * 处理半开启状态的逻辑
     */
    protected boolean invokeHalfOpenFusingStrategy() {

        return true;
    }

    /**
     * 处理关闭状态的逻辑
     */
    protected boolean invokeClosedFusingStrategy() {

        return false;
    }
}
