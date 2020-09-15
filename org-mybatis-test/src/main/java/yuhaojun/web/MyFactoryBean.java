package yuhaojun.web;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import yuhaojun.mapper.TDao;

/**
 * 第三种手动注入方式 继承FactoryBean
 * 使用这种方式会往spring单例池中注入两个bean  一个是  MyFactoryBean这个bean  另一个就是 getObject() 这个方法中返回的对象
 * 但是如果你想从单例池中通过key拿到 类的bean 需要使用 &myFactoryBean 拿到对象的bean 需要使用 myFactoryBean
 * mybaits 中默认也是使用的这种方法
 * 但是 直接使用 @component 这种方式是不行的，因为你不知道程序员想要交给spring那些类，所以你就不能写死
 * 通过 xml的方式是可以的，但是太过于麻烦于冗余 也不推荐
 * 通过@bean的方式也可以，但是你需要手动写 也很麻烦
 * <p>
 * 所以！！！！ 使用了spring自己提供的扩展点  后置处理器
 */


public class MyFactoryBean implements FactoryBean {

	//创建beanDefinition 手动传入包路径
	Class clazz;

	@Override
	public Object getObject() throws Exception {
		TDao MyTDao = (TDao) Demo.getMapper(clazz);
		return MyTDao;
	}

	@Override
	public Class<?> getObjectType() {
		return null;
	}
}
