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
package org.mybatis.spring.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * A {@link ImportBeanDefinitionRegistrar} to allow annotation configuration of
 * MyBatis mapper scanning. Using an @Enable annotation allows beans to be
 * registered via @Component configuration, whereas implementing
 * {@code BeanDefinitionRegistryPostProcessor} will work for XML configuration.
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 * @since 1.2.0
 */

/**
 * 实现了ImportBeanDefinitionRegistrar接口，扫描该类的时候会执行 registerBeanDefinitions 方法
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		/**
		 * 获取 @MapperScan("xxxx")上的内容
		 */
		AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
		/**
		 * 扩展spring中ClassPathBeanDefinitionScanner类将类扫描成BeanDefinition
		 */
		ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);

		/**
		 * 肯定不为null
		 * 设置资源加载器，作用：扫描指定包下的class文件。
		 */
		if (resourceLoader != null) {
			scanner.setResourceLoader(resourceLoader);
		}

		/**
		 * 把MapperScan中的属性设置到ClassPathMapperScanner
		 */
		Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
		if (!Annotation.class.equals(annotationClass)) {
			scanner.setAnnotationClass(annotationClass);
		}

		Class<?> markerInterface = annoAttrs.getClass("markerInterface");
		if (!Class.class.equals(markerInterface)) {
			scanner.setMarkerInterface(markerInterface);
		}

		Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
		if (!BeanNameGenerator.class.equals(generatorClass)) {
			scanner.setBeanNameGenerator(BeanUtils.instantiateClass(generatorClass));
		}

		Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs.getClass("factoryBean");
		if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
			scanner.setMapperFactoryBean(BeanUtils.instantiateClass(mapperFactoryBeanClass));
		}
		/**
		 * 设置SqlSessionFactory的名称
		 * 这也证实了使用的 sqlSession 是 sqlSessionTemplate
		 */
		scanner.setSqlSessionTemplateBeanName(annoAttrs.getString("sqlSessionTemplateRef"));
		scanner.setSqlSessionFactoryBeanName(annoAttrs.getString("sqlSessionFactoryRef"));

		/**
		 *  获取配置的包路径
		 */
		List<String> basePackages = new ArrayList<String>();
		/**
		 * 拿到 @MpperScan("xxx") 中的value值
		 */
		for (String pkg : annoAttrs.getStringArray("value")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		/**
		 * 拿到 @MpperScan("xxx") 中的basePackages值
		 */
		for (String pkg : annoAttrs.getStringArray("basePackages")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		/**
		 * 拿到 @MpperScan("xxx") 中的basePackageClasses值
		 */
		for (Class<?> clazz : annoAttrs.getClassArray("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}
		// 注册过滤器，作用：什么类型的Mapper将会留下来。
		scanner.registerFilters();
		/**
		 * 重点！！！！
		 *  扫描指定包
		 */
		scanner.doScan(StringUtils.toStringArray(basePackages));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

}
