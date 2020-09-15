/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
class PostProcessorRegistrationDelegate {

	/**
	 * 顾名思义该方法执行了部分实现了BeanFactoryPostProcessors接口的类
	 * 为什么说是部分
	 * 实现类{
	 * 1.Spring内置的实现类 ，但是并不是全部的实现类都会被执行，只有注册成了beanDefinition并且put进了集合中的才会执行
	 * 2.程序员提供的  先执行实现接口的  在执行api调用的
	 * 3.实现了order接口得类
	 * }
	 * 目前我的理解是  想要执行实现类的方法必须首先存在于bd的map中 ，并且执行完了 直接放入 单例池中
	 * BeanFactoryPostProcessor 直接子类再扫描装配bd完成后执行
	 * BeanDefinitionRegistryPostProcessor 间接子类在扫描过程中执行
	 *
	 * @param beanFactory
	 * @param beanFactoryPostProcessors
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		//存储所有已经执行的  BeanFactoryPostProcessor(BeanFactoryPostProcessor + BeanDefinitionRegistryPostProcessor)实现类的 ！名字！
		Set<String> processedBeans = new HashSet<>();

		//beanFactory实现了BeanDefinitionRegistryd的接口，所以 99%为true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			//存放执行完毕 或者 先缓存 的实现了BeanFactoryPostProcessor接口的类  ！对象 ！
			List<BeanFactoryPostProcessor> regularPostProcessors = new LinkedList<>();
			//存放执行完毕 或者 先缓存 的实现了BeanDefinitionRegistryPostProcessor接口的类的！对象 ！
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new LinkedList<>();

			/**
			 * 因为spring允许用户自己往beanFactory中添加实现了BeanFactoryPostProcessor接口的实现类
			 * 所以他会先去执行已经添加进去的
			 * 并且顺序依旧是先执行 BeanDefinitionRegistryPostProcessor  但是只添加不执行 BeanDefinitionPostProcessor
			 * 并且会在执行 BeanFactoryPostProcessor 之前先执行已经存在集合中的处理器
			 *
			 * 为什么spring可以让用户自己添加。因为如果你想在扫描之前执行后置处理器 ，或者在扫描之前做点事
			 * 那么这个方法就可行
			 */
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					//添加且执行
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				} else {
					//添加不执行
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			/**
			 * 存储的是当前需要执行的BeanFactoryPostProcessor的实现类，执行完成后remove掉
			 * 从源码看他是先执行的间接子类
			 */
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			/**
			 * 第一次先执行所有Spring中实现BeanDefinitionRegistryPostProcessor的实现类
			 */
			//根据beanDefinition的names集合先执行内置的实现类 ，因为此时并没有扫开始描自己的bean
			//此时只有一个  ConfigutionClassPostProcessor  这个beanDefinition
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					//添加到正在执行实现类的集合中去 并且是通过getBean
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//添加到已经执行完毕实现类的集合中
					processedBeans.add(ppName);
				}
			}
			//排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//合并
			registryProcessors.addAll(currentRegistryProcessors);
			//真正的执行方法 同时完成了扫描 生成beanDefinition 放入集合的的一系列操作
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//清空正在执行的实现类集合
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered
			/**
			 * 第二次执行实现order接口的自定义BeanDefinitionRegistryPostProcessor的实现类
			 */
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			for (String ppName : postProcessorNames) {
				//先判断是否存在与已经执行过的实现类集合
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			/**
			 * 最后一次执行没有实现order接口的自定义实现类
			 */
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}


			/**
			 *  上述代码是对实现了BeanDefinitionRegistryPostProcessor的类进行执行和封装成beanDefinition
			 */

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			/**
			 * 开始执行实现BeanFactoryPostProcessors的接口的类
			 */

			/**
			 *  首先执行的是registryProcessors这个集合中已经完成执行的实现了BeanDefinitionRegistryPostProcessor的类
			 *  为什么，因为  BeanDefinitionRegistryPostProcessor 是   BeanDefinitionPostProcessor的子类
			 *  同样可以实现 BeanDefinitionPostProcessor 接口中的方法
			 *
			 *  最重要的就是执行 ConfigurationClassPostProcessor 这个内置类的 postProcessBeanFactory 方法
			 *  把全配置类变成cglib代理类
			 *  cglib代理类不会对原来的方法进行修改，
			 *  先判断是否有过滤器对该方法进行过滤，如果有则去执行过滤器的方法
			 *  如果没有则执行原来的方法
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			/**
			 * 接着因为程序员自己通过api注入的实现  BeanDefinitionPostProcessor 的类一开是他是缓存起来了
			 * 所以接着执行手动注入的实现 BeanDefinitionPostProcessor 的类
			 */
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		/**
		 * 接着他就拿到所有spring内置的和程序员实现了 beanDefinitionPostProcessor 和 beanDefinitionRegisterPostProcessor 接口的类
		 */
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		/**
		 * 存放的是所有实现了  beanDefinitionPostProcessor 和 priorityOrdered接口的类的对象
		 */
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		/**
		 * 存放的是所有实现了  beanDefinitionPostProcessor 和ordered接口的类的名字
		 */
		List<String> orderedPostProcessorNames = new ArrayList<>();
		/**
		 *  存放的没是实现 beanDefinitionPostProcessor 和 ordered接口的类的名字
		 */
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		
		for (String ppName : postProcessorNames) {
			//判断是否存在于执行过了的集合中  processedBeans
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
				//判断并添加入 实现了 priorityOrderedPostProcessors 接口的集合中    并且会放到单例池中
			} else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
				//判断并添加入 实现了 orderedPostProcessor 接口的集合中
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				//都不满足添加进没有实现其他接口的集合中
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		/**
		 * 实现了priorityOrderedPostProcessors的集合排序并且执行
		 */
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		/**
		 *  根据实现了orderedPostProcessors的集合名字 拿到对象放入对象集合
		 */
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//排序且执行
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		/**
		 * 最后一步 根据没有实现order接口的类的名字的集合，拿到对象放入对象集合中并且执行
		 */
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values..
		// .
		//清除元数据缓存
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		/**
		 * 从 beanDefinition Map中 拿到所有 beanDefinition 判断是否实现beanPostProcessor 接口
		 */
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;

		/**
		 *   添加了第三个后置处理器，负责检查单例池中，是否存在实现了后置处理器，但是没有执行的bean
		 */
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				/**
				 *   从单例池中拿到实现了 beanPostProcessor 的 bean 并放到集合变量保存起来
				 */
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);

		/**
		 *
		 * 必须先存在于beanDefinition Map中
		 *
		 *  添加了三个系统内置的后置处理器
		 *  1. CommonAnnotationBeanPostProcessor      解析方法上的通用注解
		 *  2. AutowiredAnnotationBeanPostProcessor   解析方法上的 @Autowired 注解
		 *  3. RequiredAnnotationBeanPostProcessor    解析方法上的 @Required  注解
		 *
		 *  还添加了你自己在配置类上开启的后置处理器 例如：@EnableAspectJAutoProxy
		 *  还添加了你自定义的后置处理器
		 */
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		/**
		 * 添加了第七个后置处理器
		 * 该BeanPostProcessor检测那些实现了接口ApplicationListener的bean，在它们创建时初始化之后，
		 * 将它们添加到应用上下文的事件多播器上；并在这些ApplicationListener bean销毁之前，
		 * 将它们从应用上下文的事件多播器上移除
		 */
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
