package yuhaojun;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import yuhaojun.config.MyConfig;
import yuhaojun.di.Student;

public class demo {
	public static void main(String[] args) {
		/**
		 Bean的详细版生命周期流程
		 1.实例化Spring容器
		 2.扫描所有交给Spring代理的类
		 2.解析这些类
		 3.实例化beanDefinition
		 4.把这些beanDefinition放到一个Map中保存起来
		 4.1执行自定义BeanDefinitionRegistryPostProcessor实现的方法 , 上面的123部都是执行的该接口实现的方法，再执行自定义的方法
		 5.执行beanFactoryPostProcessor这些方法,该方法就是对beanDefinition进行处理或者修改
		 1.先执行Spring当中方法
		 2.在执行自己定义的方法
		 6.遍历Map对每一个beanDefinition进行验证,例如单例、懒加载..筛选出需要被创建的class类
		 7.对class类进行一系列复杂的推断,得到通过哪一个构造方法实例化对象
		 8.通过反射构造方法把该对象new出来
		 9.缓存,注解信息,解析合并后的beanDefinition对象 -------该流程很难目前不懂
		 10.暴露一个工厂,也就是循环依赖中的二级缓存
		 11.判断是否需要对该对象中的属性进行注入,Spring中默认是需要的,可以设成不需要
		 12.如果需要进行属性注入
		 13.回调部分Aware接口
		 14.执行生命周期初始化回调方法注解版 @PostConstruct,该Aware比较重要,单独列出来写
		 15.执行生命周期初始化回调方法接口版 implements InitializingBean
		 16.执行生命周期初始化回调xml版,init-method = ""
		 17.执行剩下的Aware回调方法
		 18.对该对象进行代理 aop
		 19.把该对象put进Spring单例池
		 20.销毁Spring单例池中的该对象
		 */

		/**
		 *     Spring的AOP
		 *     	1.spring中提供的aop
		 *     	2.AspectJ
		 *     这两者的关系是竞争，为什么spring中会有 @AspectJ这个注解
		 *     因为spring原来的开启AOP的语法复杂，很麻烦
		 *     使用了AspectJ 则会很简单 @AspectJ  @Pointcut("execution(* *.saying(..))") 就可以搞定
		 */


		/**
		 * 为什么在spring当中既可以通过xml初始化 也可以通过配置类初始化  并且他们可以同时存在
		 *
		 * 因为：
		 * ApplicationContext允许上下文嵌套，装饰器模式   通过保持父上下文可以维持一个上下文体系。对于Bean的查找
		 * 可以在这个上下文体系中发生，首先检查当前上下文，其次是父上下文，逐级向上，这样为不同的Spring
		 * 应用提供了一个共享的Bean定义环境。只会有一个spring单例池
		 */
		//生成一个Spring容器,但是没有初始化
		//扫描配置类
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(MyConfig.class);
		//通过xml文件来初始化容器  定位、加载和注册三个步骤
		AbstractXmlApplicationContext axc = new ClassPathXmlApplicationContext("xxx.xml");

		/*
			理解什么是Spring的Bean
				1.他不一定有完整的生命周期
				2.他一定是一个对象
				3.她一定存在容器中的单例池中
		*/

		//手动往单例池注入注入 这种方式没有生命周期
		//ac.getBeanFactory().registerSingleton("z", new student());
		//直接从存放bean的单例池拿
		Student student = ac.getBean(Student.class);
		student.getName();
		//先从beanDefinition的Map中通过类型找到名字,再去单例池拿
		//ac.getBean(student.class);
	}

	//通过@bean虽然也是手动注入但是可以走生命周期
	@Bean
	public Student setStudent() {
		return new Student();
	}

}
