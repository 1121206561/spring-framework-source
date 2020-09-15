/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.util.function.Supplier;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Standalone application context, accepting annotated classes as input - in particular
 * {@link Configuration @Configuration}-annotated classes, but also plain
 * {@link org.springframework.stereotype.Component @Component} types and JSR-330 compliant
 * classes using {@code javax.inject} annotations. Allows for registering classes one by
 * one using {@link #register(Class...)} as well as for classpath scanning using
 * {@link #scan(String...)}.
 *
 * <p>In case of multiple {@code @Configuration} classes, @{@link Bean} methods defined in
 * later classes will override those defined in earlier classes. This can be leveraged to
 * deliberately override certain bean definitions via an extra {@code @Configuration}
 * class.
 *
 * <p>See @{@link Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 * @since 3.0
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

	private final AnnotatedBeanDefinitionReader reader;

	private final ClassPathBeanDefinitionScanner scanner;


	/**
	 * Create a new AnnotationConfigApplicationContext that needs to be populated
	 * through {@link #register} calls and then manually {@linkplain #refresh refreshed}.
	 */
	public AnnotationConfigApplicationContext() {
		super();
		//AnnotatedBeanDefinitionReader的作用
		// 1、主要是可以动态、显示的注册一个bean；2、而且具备解析一个类的功能；和扫描解析一个类的功能相同；
		// AnnotatedBeanDefinitionReader的应用场景
		// 1、可以显示、动态注册一个程序员提供的bean； 配置类就是通过该扫描器put进beanDefiniton集合中
		// 2、在初始化spring容器的过程中他完成了对配置类的注册和解析功能；

		/**
		 *
		 * 	spring当中有4种beanDefinition
		 * 	  1. 加了@Compoent    AnnotatedBeanDefinitionReader
		 * 	  2.  Xml中描述的
		 * 	  3.  加了 @bean
		 * 	  4.  sring中内置的    RootBeanDefinitionReader
		 *
		 *	完成内置的beanDefinition  put进map中
		 *  读取 加了注解的bean
		 */
		this.reader = new AnnotatedBeanDefinitionReader(this);
		/**
		 * 扫描器 这个扫描器他其实并不能扫描我们加了注解的类
		 * 他只是去扫描我们通过api传递的包路径    this.scan()
		 */
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * Create a new AnnotationConfigApplicationContext with the given DefaultListableBeanFactory.
	 *
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * Create a new AnnotationConfigApplicationContext, deriving bean definitions
	 * from the given annotated classes and automatically refreshing the context.
	 *
	 * @param annotatedClasses one or more annotated classes,
	 *                         e.g. {@link Configuration @Configuration} classes
	 */
	//生成Spring容器的构造方法,需要传递一个Config.class的配置文件
	public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
		//调用无参的构造方法
		//beanDefinition的Map初始化 ,先把Spring自带的beanDefinition放进去
		this();
		//手动注入 , 把配置类转换成beanDefinition放入map中
		//为什么需要先手动注入配置类 , 大部分配置类只加了注解 @ComponentScan() 扫描不到  springboot中用了 @Configuration可以被扫描到
		// 因为你是需要通过配置类中 @bean 去生成bean  , 只有先导进去才找得到
		register(annotatedClasses);
		//可以手动关闭循环依赖,正常不能修改源码则在创建容器的时候修改
		setAllowBeanDefinitionOverriding(false);
		//进行具体的操作方法单例bean的创建流程和自动注入等功能

		//扫描所有加了@compant等注解的类

		/**
		[1]当spring容器启动的时候会去调用ConfigurationClassPostProcessor这个bean工厂的后置处理器完成扫描，
		其实所谓的spring扫描就是把类的信息读取到，但是读取到类的信息存放到哪里呢？比如类的类型(class),比如类的名字，类的构造方法。
		可能会有疑问这些信息不需要存啊，直接存在class对象里面不就可以？比如当spring扫描到X的时候Class clazzx = X.class；
		那么这个classx里面就已经具备的前面说的那些信息了，
		确实如此，但是spring实例化一个bean不仅仅只需要这些信息，还有我上文说到的scope，lazy，dependsOn等等信息需要存储，
		所以spring设计了一个BeanDefintion的类用来存储这些信息。故而当spring读取到类的信息之后
		[2]会实例化一个BeanDefinition的对象，继而调用这个对象的各种set方法存储信息；每扫描到一个符合规则的类，spring都会实例化一个BeanDefinition对象，
		然后把根据类的类名生成一个bean的名字（比如一个类IndexService，spring会根据类名IndexService生成一个bean的名字`indexService`,spring内部有一套默认的名字生成规则，但是程序员可以提供自己的名字生成器覆盖spring内置的，这个后面更新），
		[3]继而spring会把这个beanDefinition对象和生成的beanName放到一个map当中，key=beanName，value=beanDefinition对象；至此上图的第①②③步完成。
		*/

		//该方法就是自己手动往集合中添加后置处理器，并且先执行
		//addBeanFactoryPostProcessor((BeanFactoryPostProcessor) new AnnotationConfigApplicationContext());

		refresh();
	}

	/**
	 * Create a new AnnotationConfigApplicationContext, scanning for bean definitions
	 * in the given packages and automatically refreshing the context.
	 *
	 * @param basePackages the packages to check for annotated classes
	 */
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages);
		refresh();
	}


	/**
	 * {@inheritDoc}
	 * <p>Delegates given environment to underlying {@link AnnotatedBeanDefinitionReader}
	 * and {@link ClassPathBeanDefinitionScanner} members.
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		super.setEnvironment(environment);
		this.reader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
	}

	/**
	 * Provide a custom {@link BeanNameGenerator} for use with {@link AnnotatedBeanDefinitionReader}
	 * and/or {@link ClassPathBeanDefinitionScanner}, if any.
	 * <p>Default is {@link org.springframework.context.annotation.AnnotationBeanNameGenerator}.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 *
	 * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
	 * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.reader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);
		getBeanFactory().registerSingleton(
				AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
	}

	/**
	 * Set the {@link ScopeMetadataResolver} to use for detected bean classes.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 */
	public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
		this.reader.setScopeMetadataResolver(scopeMetadataResolver);
		this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
	}


	//---------------------------------------------------------------------
	// Implementation of AnnotationConfigRegistry
	//---------------------------------------------------------------------

	/**
	 * Register one or more annotated classes to be processed.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 *
	 * @param annotatedClasses one or more annotated classes,
	 *                         e.g. {@link Configuration @Configuration} classes
	 * @see #scan(String...)
	 * @see #refresh()
	 */
	public void register(Class<?>... annotatedClasses) {
		Assert.notEmpty(annotatedClasses, "At least one annotated class must be specified");
		this.reader.register(annotatedClasses);
	}

	/**
	 * Perform a scan within the specified base packages.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 *
	 * @param basePackages the packages to check for annotated classes
	 * @see #register(Class...)
	 * @see #refresh()
	 */
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		this.scanner.scan(basePackages);
	}


	//---------------------------------------------------------------------
	// Convenient methods for registering individual beans
	//---------------------------------------------------------------------

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations, and optionally providing explicit constructor
	 * arguments for consideration in the autowiring process.
	 * <p>The bean name will be generated according to annotated component rules.
	 *
	 * @param annotatedClass       the class of the bean
	 * @param constructorArguments argument values to be fed into Spring's
	 *                             constructor resolution algorithm, resolving either all arguments or just
	 *                             specific ones, with the rest to be resolved through regular autowiring
	 *                             (may be {@code null} or empty)
	 * @since 5.0
	 */
	public <T> void registerBean(Class<T> annotatedClass, Object... constructorArguments) {
		registerBean(null, annotatedClass, constructorArguments);
	}

	/**
	 * Register a bean from the given bean class, deriving its metadata from
	 * class-declared annotations, and optionally providing explicit constructor
	 * arguments for consideration in the autowiring process.
	 *
	 * @param beanName             the name of the bean (may be {@code null})
	 * @param annotatedClass       the class of the bean
	 * @param constructorArguments argument values to be fed into Spring's
	 *                             constructor resolution algorithm, resolving either all arguments or just
	 *                             specific ones, with the rest to be resolved through regular autowiring
	 *                             (may be {@code null} or empty)
	 * @since 5.0
	 */
	public <T> void registerBean(@Nullable String beanName, Class<T> annotatedClass, Object... constructorArguments) {
		this.reader.doRegisterBean(annotatedClass, null, beanName, null,
				bd -> {
					for (Object arg : constructorArguments) {
						bd.getConstructorArgumentValues().addGenericArgumentValue(arg);
					}
				});
	}

	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, @Nullable Supplier<T> supplier,
								 BeanDefinitionCustomizer... customizers) {

		this.reader.doRegisterBean(beanClass, supplier, beanName, null, customizers);
	}

}
