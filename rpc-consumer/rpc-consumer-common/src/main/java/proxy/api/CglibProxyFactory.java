package proxy.api;
import io.zz.rpc.spi.annotation.SPIClass;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * @Author: zhouzhong
 * @Email: 21212010059@m.fudan.edu.cn
 * @Date: 2023/3/20 23:13
 * @Description:
 */
@SPIClass
public class CglibProxyFactory <T> extends BaseProxyFactory<T> implements ProxyFactory {
    private final Logger logger = LoggerFactory.getLogger(CglibProxyFactory.class);
    private final Enhancer enhancer = new Enhancer();
    @Override
    public <T> T getProxy(Class<T> clazz) {
        logger.info("基于CGLib动态代理...");
        enhancer.setInterfaces(new Class[]{clazz});
        enhancer.setCallback(new InvocationHandler() {
            @Override
            public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                return objectProxy.invoke(o, method, objects);
            }
        });
        return (T) enhancer.create();
    }
}
