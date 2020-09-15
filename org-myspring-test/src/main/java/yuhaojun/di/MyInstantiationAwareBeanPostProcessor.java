package yuhaojun.di;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

//@Component
public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		try {
			Class c = Class.forName(beanClass.getName());
			//那么他就会直接把该对象放入单例池，没有其他的属性填充，进行后置处理器.....等
			return c.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
