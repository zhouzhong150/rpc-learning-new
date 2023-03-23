package proxy.api;

import io.zz.rpc.spi.annotation.SPIClass;

import java.lang.reflect.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @Author: zhouzhong
 * @Email: 21212010059@m.fudan.edu.cn
 * @Date: 2023/3/20 23:10
 * @Description:
 */
@SPIClass
public class JdkProxyFactory<T> extends BaseProxyFactory<T> implements ProxyFactory {
    private final Logger logger = LoggerFactory.getLogger(JdkProxyFactory.class);
    @Override
    public <T> T getProxy(Class<T> clazz) {
        logger.info("基于JDK动态代理...");
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                objectProxy
        );
    }
}
