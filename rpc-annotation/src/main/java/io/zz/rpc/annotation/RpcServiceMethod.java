package io.zz.rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcServiceMethod {

    /**
     * 是否开启结果缓存
     */
    boolean enableResultCache() default false;

    /**
     * 是否开启限流
     */
    boolean enableRateLimiter() default false;

    /**
     * 在milliSeconds毫秒内最多能够通过的请求个数
     */
    int permits() default 10;

    /**
     * 毫秒数
     */
    int milliSeconds() default 10;

    /**
     * 限流器类型
     */
    String rateLimiterType() default "";

    /**
     * 当限流失败时的处理策略
     */
    String rateLimiterFailStrategy() default "";

    /**
     * 是否允许熔断
     */
    boolean enableFusing() default false;

    /**
     * 熔断类型
     */
    String fusingType() default "";

    /**
     * 在milliSeconds毫秒内触发熔断操作的上限值
     */
    double totalFailure() default 20;

    /**
     * 毫秒数
     */
    int fusingMilliSeconds() default 30000;

}
