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

package org.springframework.beans.factory.annotation;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that autowires annotated fields, setter methods and arbitrary config methods.
 * Such members to be injected are detected through a Java 5 annotation: by default,
 * Spring's {@link Autowired @Autowired} and {@link Value @Value} annotations.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
 * if available, as a direct alternative to Spring's own {@code @Autowired}.
 *
 * <p>Only one constructor (at max) of any given bean class may carry this
 * annotation with the 'required' parameter set to {@code true},
 * indicating <i>the</i> constructor to autowire when used as a Spring bean.
 * If multiple <i>non-required</i> constructors carry the annotation, they
 * will be considered as candidates for autowiring. The constructor with
 * the greatest number of dependencies that can be satisfied by matching
 * beans in the Spring container will be chosen. If none of the candidates
 * can be satisfied, then a default constructor (if present) will be used.
 * An annotated constructor does not have to be public.
 *
 * <p>Fields are injected right after construction of a bean, before any
 * config methods are invoked. Such a config field does not have to be public.
 *
 * <p>Config methods may have an arbitrary name and any number of arguments; each of
 * those arguments will be autowired with a matching bean in the Spring container.
 * Bean property setter methods are effectively just a special case of such a
 * general config method. Config methods do not have to be public.
 *
 * <p>Note: A default AutowiredAnnotationBeanPostProcessor will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom AutowiredAnnotationBeanPostProcessor bean definition.
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection;
 * thus the latter configuration will override the former for properties wired through
 * both approaches.
 *
 * <p>In addition to regular injection points as discussed above, this post-processor
 * also handles Spring's {@link Lookup @Lookup} annotation which identifies lookup
 * methods to be replaced by the container at runtime. This is essentially a type-safe
 * version of {@code getBean(Class, args)} and {@code getBean(String, args)},
 * See {@link Lookup @Lookup's javadoc} for details.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sebastien Deleuze
 * @since 2.5
 * @see #setAutowiredAnnotationType
 * @see Autowired
 * @see Value
 */
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
		implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>();

	private String requiredParameterName = "required";

	private boolean requiredParameterValue = true;

	private int order = Ordered.LOWEST_PRECEDENCE - 2;

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	private final Set<String> lookupMethodsChecked = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);

	private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * Create a new AutowiredAnnotationBeanPostProcessor
	 * for Spring's standard {@link Autowired} annotation.
	 * <p>Also supports JSR-330's {@link javax.inject.Inject} annotation, if available.
	 */
	@SuppressWarnings("unchecked")
	public AutowiredAnnotationBeanPostProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.info("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}


	/**
	 * Set the 'autowired' annotation type, to be used on constructors, fields,
	 * setter methods and arbitrary config methods.
	 * <p>The default autowired annotation type is the Spring-provided
	 * {@link Autowired} annotation, as well as {@link Value}.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a member is
	 * supposed to be autowired.
	 */
	public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
		Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.add(autowiredAnnotationType);
	}

	/**
	 * Set the 'autowired' annotation types, to be used on constructors, fields,
	 * setter methods and arbitrary config methods.
	 * <p>The default autowired annotation type is the Spring-provided
	 * {@link Autowired} annotation, as well as {@link Value}.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation types to indicate that a member is
	 * supposed to be autowired.
	 */
	public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
		Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
	}

	/**
	 * Set the name of a parameter of the annotation that specifies
	 * whether it is required.
	 * @see #setRequiredParameterValue(boolean)
	 */
	public void setRequiredParameterName(String requiredParameterName) {
		this.requiredParameterName = requiredParameterName;
	}

	/**
	 * Set the boolean value that marks a dependency as required
	 * <p>For example if using 'required=true' (the default),
	 * this value should be {@code true}; but if using
	 * 'optional=false', this value should be {@code false}.
	 * @see #setRequiredParameterName(String)
	 */
	public void setRequiredParameterValue(boolean requiredParameterValue) {
		this.requiredParameterValue = requiredParameterValue;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
		metadata.checkConfigMembers(beanDefinition);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName)
			throws BeanCreationException {

		// Let's check for lookup methods here..
		/**
		 * 处理含有@Lookup注解的方法
		 * 如果集合中没有BeanName，则走一遍Bean中所有的方法，过滤是否含有Lookup方法
		 */
		if (!this.lookupMethodsChecked.contains(beanName)) {
			try {
				/**
				 * 在方法中循环过滤所有的方法
				 */
				ReflectionUtils.doWithMethods(beanClass, method -> {
					/**
					 * 获取method上的@Lookup注解
					 */
					Lookup lookup = method.getAnnotation(Lookup.class);
					/**
					 * 存在此注解的话，就将方法和注解中的内容构建LookupOverride对象，设置进BeanDefinition中
					 */
					if (lookup != null) {
						Assert.state(beanFactory != null, "No BeanFactory available");
						LookupOverride override = new LookupOverride(method, lookup.value());
						try {
							RootBeanDefinition mbd = (RootBeanDefinition) beanFactory.getMergedBeanDefinition(beanName);
							mbd.getMethodOverrides().addOverride(override);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(beanName,
								"Cannot apply @Lookup to beans without corresponding bean definition");
						}
					}
				});
			}
			catch (IllegalStateException ex) {
				throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
			}
			/**
			 * 无论对象中是否含有@Lookup方法，过滤完成后都会放到集合中，证明此Bean已经检查完@Lookup注解了
			 */
			this.lookupMethodsChecked.add(beanName);
		}

		// Quick check on the concurrent map first, with minimal locking.
		/**
		 * 从缓存中拿构造函数，不存在的话就进入代码块中再拿一遍，还不存在的话就进行下方的逻辑
		 * 同时这个参数是最终返回的
		 * 这里是双重检查
		 */
		Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
		if (candidateConstructors == null) {
			// Fully synchronized resolution now...
			synchronized (this.candidateConstructorsCache) {
				//再拿一次，双重检查
				candidateConstructors = this.candidateConstructorsCache.get(beanClass);
				if (candidateConstructors == null) {
					//声明一个变量用来接收 Bean 中所有的构造函数
					Constructor<?>[] rawCandidates;
					try {
						/**
						 * 获取Bean中所有的构造函数
						 */
						rawCandidates = beanClass.getDeclaredConstructors();
					}
					catch (Throwable ex) {
						throw new BeanCreationException(beanName,
								"Resolution of declared constructors on bean Class [" + beanClass.getName() +
								"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
					}
					/**
					 * 暂时候选构造函数集合，通过下面代码得知，这个集合中只能有一个值
					 */
					List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
					/**
					 * 带有依赖项的构造函数
					 */
					Constructor<?> requiredConstructor = null;
					/**
					 * 默认使用的构造函数
					 */
					Constructor<?> defaultConstructor = null;
					/**
					 * 获取主构造函数(在Klion语言中存在),下方关于这个变量不再解释，不研究
					 */
					Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
					/**
					 * 标识，表示不是合成构造函数的数量
					 * 合成构造函数 -> 有方法参数并对实例进行赋值的构造函数
					 */
					int nonSyntheticConstructors = 0;
					/**
					 * 遍历所有的构造函数
					 */
					for (Constructor<?> candidate : rawCandidates) {
						/**
						 * 是否是内部类，如果是非静态内部类，编译器在编译的过程中会对其中的方法加入关键字Synthetic
						 * 和外部类进行区分
						 */
						if (!candidate.isSynthetic()) {
							nonSyntheticConstructors++;
						}
						else if (primaryConstructor != null) {
							continue;
						}    
						/**
						 * 查找构造函数上{@link Autowired}注解的属性
						 * 断点应该还判断{@link Value}的属性，但是此注解不能注释在构造函数上，暂时不理解
						 */
						AnnotationAttributes ann = findAutowiredAnnotation(candidate);
						/**
						 * 注解不存在,则再通过方法获取用户类，如果是用户类则返回用户类，还判断了CGLIB情况，
						 * CGLIB情况则返回目标类，然后获取参数一致的构造函数再获取注解
						 */
						if (ann == null) {
							Class<?> userClass = ClassUtils.getUserClass(beanClass);
							if (userClass != beanClass) {
								try {
									Constructor<?> superCtor =
											userClass.getDeclaredConstructor(candidate.getParameterTypes());
									ann = findAutowiredAnnotation(superCtor);
								}
								catch (NoSuchMethodException ex) {
									// Simply proceed, no equivalent superclass constructor found...
								}
							}
						}
						/**
						 * 构造函数上存在注解
						 */
						if (ann != null) {
							if (requiredConstructor != null) {
								throw new BeanCreationException(beanName,
										"Invalid autowire-marked constructor: " + candidate +
										". Found constructor with 'required' Autowired annotation already: " +
										requiredConstructor);
							}
							/**
							 * 获取@Autowired注解中required属性的值
							 */
							boolean required = determineRequiredStatus(ann);

							/**
							 * 为true则将这个构造函数设置为带有依赖项的构造函数
							 * 并进行一个判断，不可存在多个带有依赖项的构造函数
							 */
							if (required) {
								if (!candidates.isEmpty()) {
									throw new BeanCreationException(beanName,
											"Invalid autowire-marked constructors: " + candidates +
											". Found constructor with 'required' Autowired annotation: " +
											candidate);
								}
								requiredConstructor = candidate;
							}
							candidates.add(candidate);
						}
						/**
						 * 如果构造函数的参数为零，则是默认构造函数
						 */
						else if (candidate.getParameterCount() == 0) {
							defaultConstructor = candidate;
						}
					}
					/**
					 * 存在@Autowired注解的函数，并且required值为false，则此注解不起作用
					 * 但是存在默认构造函数，则将默认构造函数添加进集合中
					 * 并将集合变为数组使用
					 */
					if (!candidates.isEmpty()) {
						// Add default constructor to list of optional constructors, as fallback.
						if (requiredConstructor == null) {
							if (defaultConstructor != null) {
								candidates.add(defaultConstructor);
							}
							else if (candidates.size() == 1 && logger.isWarnEnabled()) {
								logger.warn("Inconsistent constructor declaration on bean with name '" + beanName +
										"': single autowire-marked constructor flagged as optional - " +
										"this constructor is effectively required since there is no " +
										"default constructor to fall back to: " + candidates.get(0));
							}
						}
						candidateConstructors = candidates.toArray(new Constructor<?>[candidates.size()]);
					}
					/**
					 * 如果只存在一个构造函数，且这个构造函数有参数列表，则使用这个构造函数
					 */
					else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
						candidateConstructors = new Constructor<?>[] {rawCandidates[0]};
					}
					/**
					 * ----忽略----  (注释还是加上吧)
					 * 如果非合成构造存在两个且有主构造和默认构造，且主构造和默认构造不相等
					 * 这两个一块使用
					 */
					else if (nonSyntheticConstructors == 2 && primaryConstructor != null
							&& defaultConstructor != null && !primaryConstructor.equals(defaultConstructor)) {
						candidateConstructors = new Constructor<?>[] {primaryConstructor, defaultConstructor};
					}
					/**
					 * ----忽略----  (注释还是加上吧)
					 * 如果只有一个非合成构造且有主构造，使用主构造器
					 */
					else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
						candidateConstructors = new Constructor<?>[] {primaryConstructor};
					}
					/**
					 * 否则没有能够直接使用的构造器
					 */
					else {
						candidateConstructors = new Constructor<?>[0];
					}
					this.candidateConstructorsCache.put(beanClass, candidateConstructors);
				}
			}
		}
		/**
		 * 使用构造列表中数据，没有值则返回null
		 */
		return (candidateConstructors.length > 0 ? candidateConstructors : null);
	}

	@Override
	public PropertyValues postProcessPropertyValues(
			PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeanCreationException {
		/**
		 * 获取需要注入的属性的元信息（类上的父类、注解。。。。等等）
		 */
		InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
		try {
			/**
			 * 遍历其中需要解析的属性或方法，进行解析
			 */
			metadata.inject(bean, beanName, pvs);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
		}
		return pvs;
	}

	/**
	 * 'Native' processing method for direct calls with an arbitrary target instance,
	 * resolving all of its fields and methods which are annotated with {@code @Autowired}.
	 * @param bean the target instance to process
	 * @throws BeanCreationException if autowiring failed
	 */
	public void processInjection(Object bean) throws BeanCreationException {
		Class<?> clazz = bean.getClass();
		InjectionMetadata metadata = findAutowiringMetadata(clazz.getName(), clazz, null);
		try {
			metadata.inject(bean, null, null);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					"Injection of autowired dependencies failed for class [" + clazz + "]", ex);
		}
	}


	private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
		// Fall back to class name as cache key, for backwards compatibility with custom callers.
		/**
		 * 生成缓存的key
		 */
		String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
		// Quick check on the concurrent map first, with minimal locking.
		/**
		 * 查看缓存中有没有
		 */
		InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
		/**
		 * 缓存中没有或者缓存中的Class与现在的Class不一样，则进行分析查找自动注入的属性
		 * if代码块中用到了双重检查
		 */
		if (InjectionMetadata.needsRefresh(metadata, clazz)) {
			synchronized (this.injectionMetadataCache) {
				metadata = this.injectionMetadataCache.get(cacheKey);
				/**
				 * 缓存中存在但是Class已经不一致了，则将pvs中封装的参数清除
				 */
				if (InjectionMetadata.needsRefresh(metadata, clazz)) {
					if (metadata != null) {
						metadata.clear(pvs);
					}
					/**
					 * 解析需要注入的属性元信息，并缓存起来
					 * 重要！！！
					 */
					metadata = buildAutowiringMetadata(clazz);
					this.injectionMetadataCache.put(cacheKey, metadata);
				}
			}
		}
		return metadata;
	}

	private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
		LinkedList<InjectionMetadata.InjectedElement> elements = new LinkedList<>();
		Class<?> targetClass = clazz;

		do {
			final LinkedList<InjectionMetadata.InjectedElement> currElements = new LinkedList<>();
			/**
			 * 首先查找需要注入的属性
			 */
			ReflectionUtils.doWithLocalFields(targetClass, field -> {
				/**
				 * 查找属性上的注入注解
				 */
				AnnotationAttributes ann = findAutowiredAnnotation(field);
				if (ann != null) {
					/**
					 * 如果属性是static修饰的，则不对其进行自动注入
					 */
					if (Modifier.isStatic(field.getModifiers())) {
						if (logger.isWarnEnabled()) {
							logger.warn("Autowired annotation is not supported on static fields: " + field);
						}
						return;
					}
					/**
					 * 是否一定要诸如成功
					 * 解析@Autowired中的required属性，为true的话注入失败会报错，为false的话允许失败
					 */
					boolean required = determineRequiredStatus(ann);
					currElements.add(new AutowiredFieldElement(field, required));
				}
			});

			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
				if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
					return;
				}
				AnnotationAttributes ann = findAutowiredAnnotation(bridgedMethod);
				if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
					if (Modifier.isStatic(method.getModifiers())) {
						if (logger.isWarnEnabled()) {
							logger.warn("Autowired annotation is not supported on static methods: " + method);
						}
						return;
					}
					if (method.getParameterCount() == 0) {
						if (logger.isWarnEnabled()) {
							logger.warn("Autowired annotation should only be used on methods with parameters: " +
									method);
						}
					}
					boolean required = determineRequiredStatus(ann);
					PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
					currElements.add(new AutowiredMethodElement(method, required, pd));
				}
			});
			elements.addAll(0, currElements);
			/**
			 * 这里遍历了父类，所以整个的分析代码是在一个大的while循环中的
			 */
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);

		return new InjectionMetadata(clazz, elements);
	}

	@Nullable
	private AnnotationAttributes findAutowiredAnnotation(AccessibleObject ao) {
		/**
		 * 循环所有符合的属性
		 */
		if (ao.getAnnotations().length > 0) {
			/**
			 *  autowiredAnnotationTypes中存在两个类型  @Autowire  和  @value
			 */
			for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
				AnnotationAttributes attributes = AnnotatedElementUtils.getMergedAnnotationAttributes(ao, type);
				if (attributes != null) {
					return attributes;
				}
			}
		}
		return null;
	}

	/**
	 * Determine if the annotated field or method requires its dependency.
	 * <p>A 'required' dependency means that autowiring should fail when no beans
	 * are found. Otherwise, the autowiring process will simply bypass the field
	 * or method when no beans are found.
	 * @param ann the Autowired annotation
	 * @return whether the annotation indicates that a dependency is required
	 */
	protected boolean determineRequiredStatus(AnnotationAttributes ann) {
		return (!ann.containsKey(this.requiredParameterName) ||
				this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
	}

	/**
	 * Obtain all beans of the given type as autowire candidates.
	 * @param type the type of the bean
	 * @return the target beans, or an empty Collection if no bean of this type is found
	 * @throws BeansException if bean retrieval failed
	 */
	protected <T> Map<String, T> findAutowireCandidates(Class<T> type) throws BeansException {
		if (this.beanFactory == null) {
			throw new IllegalStateException("No BeanFactory configured - " +
					"override the getBeanOfType method or specify the 'beanFactory' property");
		}
		return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
	}

	/**
	 * Register the specified bean as dependent on the autowired beans.
	 */
	private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
		if (beanName != null) {
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
					this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Autowiring by type from bean name '" + beanName +
							"' to bean named '" + autowiredBeanName + "'");
				}
			}
		}
	}

	/**
	 * Resolve the specified cached method argument or field value.
	 */
	@Nullable
	private Object resolvedCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
		if (cachedArgument instanceof DependencyDescriptor) {
			DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
			Assert.state(beanFactory != null, "No BeanFactory available");
			return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
		}
		else {
			return cachedArgument;
		}
	}


	/**
	 * Class representing injection information about an annotated field.
	 */
	private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object cachedFieldValue;

		public AutowiredFieldElement(Field field, boolean required) {
			super(field, null);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			/**
			 * 解析需要注入的属性，和方法思路差不过(可参阅方法的注入)
			 * 只不过方法中会有多个参数，需要遍历这些参数解析，然后利用反射执行这个方法
			 */
			Field field = (Field) this.member;
			Object value;
			if (this.cached) {
				value = resolvedCachedArgument(beanName, this.cachedFieldValue);
			}
			else {
				DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
				desc.setContainingClass(bean.getClass());
				Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
				Assert.state(beanFactory != null, "No BeanFactory available");
				TypeConverter typeConverter = beanFactory.getTypeConverter();
				try {
					/**
					 * 从单例池中获取bean
					 */
					value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
				}
				/**
				 * 进行缓存
				 */
				synchronized (this) {
					if (!this.cached) {
						if (value != null || this.required) {
							this.cachedFieldValue = desc;
							registerDependentBeans(beanName, autowiredBeanNames);
							if (autowiredBeanNames.size() == 1) {
								String autowiredBeanName = autowiredBeanNames.iterator().next();
								if (beanFactory.containsBean(autowiredBeanName)) {
									if (beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
										this.cachedFieldValue = new ShortcutDependencyDescriptor(
												desc, autowiredBeanName, field.getType());
									}
								}
							}
						}
						else {
							this.cachedFieldValue = null;
						}
						this.cached = true;
					}
				}
			}
			/**
			 * 执行方法
			 */
			if (value != null) {
				ReflectionUtils.makeAccessible(field);
				/**
				 * 属性填充
				 */
				field.set(bean, value);
			}
		}
	}


	/**
	 * Class representing injection information about an annotated method.
	 */
	private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object[] cachedMethodArguments;

		public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
			super(method, pd);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			if (checkPropertySkipping(pvs)) {
				return;
			}
			Method method = (Method) this.member;
			Object[] arguments;
			if (this.cached) {
				// Shortcut for avoiding synchronization...
				arguments = resolveCachedArguments(beanName);
			}
			else {
				Class<?>[] paramTypes = method.getParameterTypes();
				arguments = new Object[paramTypes.length];
				DependencyDescriptor[] descriptors = new DependencyDescriptor[paramTypes.length];
				Set<String> autowiredBeans = new LinkedHashSet<>(paramTypes.length);
				Assert.state(beanFactory != null, "No BeanFactory available");
				TypeConverter typeConverter = beanFactory.getTypeConverter();
				for (int i = 0; i < arguments.length; i++) {
					MethodParameter methodParam = new MethodParameter(method, i);
					DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
					currDesc.setContainingClass(bean.getClass());
					descriptors[i] = currDesc;
					try {
						Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
						if (arg == null && !this.required) {
							arguments = null;
							break;
						}
						arguments[i] = arg;
					}
					catch (BeansException ex) {
						throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
					}
				}
				synchronized (this) {
					if (!this.cached) {
						if (arguments != null) {
							Object[] cachedMethodArguments = new Object[paramTypes.length];
							System.arraycopy(descriptors, 0, cachedMethodArguments, 0, arguments.length);
							registerDependentBeans(beanName, autowiredBeans);
							if (autowiredBeans.size() == paramTypes.length) {
								Iterator<String> it = autowiredBeans.iterator();
								for (int i = 0; i < paramTypes.length; i++) {
									String autowiredBeanName = it.next();
									if (beanFactory.containsBean(autowiredBeanName)) {
										if (beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
											cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
													descriptors[i], autowiredBeanName, paramTypes[i]);
										}
									}
								}
							}
							this.cachedMethodArguments = cachedMethodArguments;
						}
						else {
							this.cachedMethodArguments = null;
						}
						this.cached = true;
					}
				}
			}
			if (arguments != null) {
				try {
					ReflectionUtils.makeAccessible(method);
					method.invoke(bean, arguments);
				}
				catch (InvocationTargetException ex){
					throw ex.getTargetException();
				}
			}
		}

		@Nullable
		private Object[] resolveCachedArguments(@Nullable String beanName) {
			Object[] cachedMethodArguments = this.cachedMethodArguments;
			if (cachedMethodArguments == null) {
				return null;
			}
			Object[] arguments = new Object[cachedMethodArguments.length];
			for (int i = 0; i < arguments.length; i++) {
				arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i]);
			}
			return arguments;
		}
	}


	/**
	 * DependencyDescriptor variant with a pre-resolved target bean name.
	 */
	@SuppressWarnings("serial")
	private static class ShortcutDependencyDescriptor extends DependencyDescriptor {

		private final String shortcut;

		private final Class<?> requiredType;

		public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut, Class<?> requiredType) {
			super(original);
			this.shortcut = shortcut;
			this.requiredType = requiredType;
		}

		@Override
		public Object resolveShortcut(BeanFactory beanFactory) {
			return beanFactory.getBean(this.shortcut, this.requiredType);
		}
	}

}
