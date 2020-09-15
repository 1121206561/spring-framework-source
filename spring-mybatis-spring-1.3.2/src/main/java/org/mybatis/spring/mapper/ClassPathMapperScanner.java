/**
 * Copyright 2010-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.mapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

/**
 * A {@link ClassPathBeanDefinitionScanner} that registers Mappers by
 * {@code basePackage}, {@code annotationClass}, or {@code markerInterface}. If
 * an {@code annotationClass} and/or {@code markerInterface} is specified, only
 * the specified types will be searched (searching for all interfaces will be
 * disabled).
 * <p>
 * This functionality was previously a private class of
 * {@link MapperScannerConfigurer}, but was broken out in version 1.2.0.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @since 1.2.0
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {

	private boolean addToConfig = true;

	private SqlSessionFactory sqlSessionFactory;

	private SqlSessionTemplate sqlSessionTemplate;

	private String sqlSessionTemplateBeanName;

	private String sqlSessionFactoryBeanName;

	private Class<? extends Annotation> annotationClass;

	private Class<?> markerInterface;

	private MapperFactoryBean<?> mapperFactoryBean = new MapperFactoryBean<Object>();

	public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
		super(registry, false);
	}

	public void setAddToConfig(boolean addToConfig) {
		this.addToConfig = addToConfig;
	}

	public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
		this.annotationClass = annotationClass;
	}

	public void setMarkerInterface(Class<?> markerInterface) {
		this.markerInterface = markerInterface;
	}

	public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
		this.sqlSessionFactory = sqlSessionFactory;
	}

	public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
		this.sqlSessionTemplate = sqlSessionTemplate;
	}

	public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
		this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
	}

	public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
		this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
	}

	public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
		this.mapperFactoryBean = mapperFactoryBean != null ? mapperFactoryBean : new MapperFactoryBean<Object>();
	}


	/**
	 * Configures parent scanner to search for the right interfaces. It can search
	 * for all interfaces or just for those that extends a markerInterface or/and
	 * those annotated with the annotationClass
	 */
	public void registerFilters() {
		boolean acceptAllInterfaces = true;

		/**
		 * 对于annotationClass属性的处理
		 * 如果annotationClass不为空，表示用户设置了此属性，那么就要根据此属性生成过滤器以保证达到用户想要的效果，
		 * 而封装此属性的过滤器就是AnnotationTypeFilter。
		 * AnnotationTypeFilter保证在扫描对应Java文件时只接受标记有注解为annotationClass的接口。
		 */
		if (this.annotationClass != null) {
			addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
			acceptAllInterfaces = false;
		}
		
		/**
		 * 对于markerInterface属性的处理
		 * 如果markerInterface不为空，表示用户设置了此属性，那么就要根据此属性生成过滤器以保证达到用户想要的效果，
		 * 而封装此属性的过滤器就是实现AssignableTypeFilter接口的局部类。
		 * 表示扫描过程中只有实现markerInterface接口的接口才会被接受。
		 */
		if (this.markerInterface != null) {
			addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
				@Override
				protected boolean matchClassName(String className) {
					return false;
				}
			});
			acceptAllInterfaces = false;
		}
		/**
		 *  默认对所有接口都扫描
		 */
		if (acceptAllInterfaces) {
			// default include filter that accepts all classes
			addIncludeFilter(new TypeFilter() {
				@Override
				public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
					return true;
				}
			});
		}

		/**
		 对于命名为package-info的Java文件，默认不作为逻辑实现接口，将其排除掉，
		 使用TypeFilter接口的局部类实现match方法。
		 从上面的函数我们看出，控制扫描文件Spring通过不同的过滤器完成，
		 这些定义的过滤器记录在了includeFilters和excludeFilters属性中。
		 */
		addExcludeFilter(new TypeFilter() {
			@Override
			public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
				String className = metadataReader.getClassMetadata().getClassName();
				return className.endsWith("package-info");
			}
		});
	}

	/**
	 * Calls the parent search that will search and register all the candidates.
	 * Then the registered objects are post processed to set them as
	 * MapperFactoryBeans
	 */
	@Override
	public Set<BeanDefinitionHolder> doScan(String... basePackages) {
		/**
		 * 调用父类的扫描器，也就是spring提供的扫描器
		 * 扫描路径，把所有interface都封装成beanDefinitionHolder，注入spring bdMap中
		 * 重点！！！
		 * 此时这个beanDefinition她只是一个mapper接口变成的，并不是具体的sql语句的方法变成beanDefinition
		 */
		Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

		if (beanDefinitions.isEmpty()) {
			logger.warn("No MyBatis mapper was found in '" + Arrays.toString(basePackages) + "' package. Please check your configuration.");
		} else {
			/**
			 * 重点！！！
			 * 继续对beanDefinition进行属性的额外设置
			 */
			processBeanDefinitions(beanDefinitions);
		}

		return beanDefinitions;
	}

	private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
		GenericBeanDefinition definition;
		/**
		 * 遍历所有的beanDefinition
		 */
		for (BeanDefinitionHolder holder : beanDefinitions) {
			definition = (GenericBeanDefinition) holder.getBeanDefinition();

			if (logger.isDebugEnabled()) {
				logger.debug("Creating MapperFactoryBean with name '" + holder.getBeanName()
						+ "' and '" + definition.getBeanClassName() + "' mapperInterface");
			}

			/**
			 * 把bd的名字(com.Mapper.xxMapper)设置为构造器的参数，也就是说反射创建对象的时候
			 * 推断带参数的构造方法
			 */
			definition.getConstructorArgumentValues().addGenericArgumentValue(definition.getBeanClassName()); // issue #59
			/**
			 * 重点！！！！
			 * 原始的接口类型的bean替换成一个MapperFactoryBean的类型。
			 * 所以它会根据 mapperFactoryBean这个类 来实例化对象
			 * 又因为该类实现了 FactoryBean 接口，所以会在 spring ioc 容器中创建两个实例
			 *  xxMapper   &xxMapper
			 *
			 * MapperFactoryBean这个类真的很重要！！！！！！！！
			 */
			definition.setBeanClass(this.mapperFactoryBean.getClass());
			/**
			 * 往beanDefinition设置各种属性
			 */
			definition.getPropertyValues().add("addToConfig", this.addToConfig);

			boolean explicitFactoryUsed = false;
			if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
				definition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
				explicitFactoryUsed = true;
			} else if (this.sqlSessionFactory != null) {
				/**
				 * 重要！！！！
				 * 
				 *  往definition设置属性值sqlSessionFactory，那么在MapperFactoryBean实例化后，
				 *  进行属性赋值时populateBean(beanName, mbd, instanceWrapper);
				 *  会通过反射调用sqlSessionFactory的set方法进行赋值
				 *  也就是在MapperFactoryBean创建实例后，要调用setSqlSessionFactory方法
				 *  将sqlSessionFactory注入进MapperFactoryBean实例
				 */
				definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
				explicitFactoryUsed = true;
			}

			if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
				if (explicitFactoryUsed) {
					logger.warn("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
				}
				definition.getPropertyValues().add("sqlSessionTemplate", new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
				explicitFactoryUsed = true;
			} else if (this.sqlSessionTemplate != null) {
				if (explicitFactoryUsed) {
					logger.warn("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
				}
				/**
				 * 添加属性 sqlSessionTemplate 和实例化方法有关系
				 */
				definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
				explicitFactoryUsed = true;
			}

			if (!explicitFactoryUsed) {
				if (logger.isDebugEnabled()) {
					logger.debug("Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
				}
				/**
				 * 设置注入属性模型为 BY_TYPE
				 * 重要！！！
				 * 这就是为什么Mybatis的MapperFactoryBean的属性没有加@Autowire，也实现了属性注入的原因
				 */
				definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
		if (super.checkCandidate(beanName, beanDefinition)) {
			return true;
		} else {
			logger.warn("Skipping MapperFactoryBean with name '" + beanName
					+ "' and '" + beanDefinition.getBeanClassName() + "' mapperInterface"
					+ ". Bean already defined with the same name!");
			return false;
		}
	}

}
