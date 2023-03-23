package io.zz.rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReferenceMethod {

    /**
     * 是否单向调用
     */
    boolean oneway() default false;

    /**
     * 是否开启结果缓存
     */
    boolean enableResultCache() default false;

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
