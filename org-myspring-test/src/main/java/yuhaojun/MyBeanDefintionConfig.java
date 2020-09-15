package yuhaojun;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import yuhaojun.di.Student;

//该方法可以对beanDefinition进行修改 , spring会通过后置处理器进行处理beanDefintion
//@Component
public class MyBeanDefintionConfig implements BeanFactoryPostProcessor {
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		//从Map中拿到你想要的BeanDefinition
		AbstractBeanDefinition person = (AbstractBeanDefinition) beanFactory.getBeanDefinition("person");
		//进行修改
		person.setBeanClass(Student.class);
	}
}
