package yuhaojun.web;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

//mybatis中是通过自定义接口实现的 现在用注入模拟一下 让spring能够扫描到这个后置处理器
public class MyBeanDefinitionRegister implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		//把一个类转换成 beanDefinition  的 转换器
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(MyFactoryBean.class);
		for (; ; ) {
			//构建一个的beanDefinition
			AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();
			/**
			 * 扫描多个类就是通过循环 把每个 属性都添加进去
			 */
			//设置属性  @MapperScan 传递过来的包路径
			beanDefinition.getPropertyValues().add("clazz", "路径: beanDefinition.getBeanClassName()");
			//还可以设置使用构造方法 得到对象
			//beanDefinition.getConstructorArgumentValues().addArgumentValues(    构造方法张参数的值   );
			//添加进beanDefinition的集合中
			registry.registerBeanDefinition("xxx", beanDefinition);
		}
	}
}
