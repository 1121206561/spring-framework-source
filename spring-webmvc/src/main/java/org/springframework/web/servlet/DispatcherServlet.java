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

package org.springframework.web.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/**
	 * Well-known name for the MultipartResolver object in the bean factory for this namespace.
	 */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/**
	 * Well-known name for the LocaleResolver object in the bean factory for this namespace.
	 */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/**
	 * Well-known name for the ThemeResolver object in the bean factory for this namespace.
	 */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 *
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 *
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 *
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 *
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * Otherwise only the global web app context is obtainable by tags etc.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with an
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/**
	 * Log category to use when no mapped handler is found for a request.
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/**
	 * Additional logger to use when no mapped handler is found for a request.
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			/**
			 * DispatcherServlet类加载的时候就会执行该静态方法
			 *  创建一个文件读取器，通过读取路径下的 DispatcherServlet.properties文件
			 *  把 key = value 封装到 defaultStrategies的properties对象中
			 *  保存了8组信息，在init()方法的时候会用到
			 */
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/**
	 * Detect all HandlerMappings or just expect "handlerMapping" bean?
	 */
	private boolean detectAllHandlerMappings = true;

	/**
	 * Detect all HandlerAdapters or just expect "handlerAdapter" bean?
	 */
	private boolean detectAllHandlerAdapters = true;

	/**
	 * Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean?
	 */
	private boolean detectAllHandlerExceptionResolvers = true;

	/**
	 * Detect all ViewResolvers or just expect "viewResolver" bean?
	 */
	private boolean detectAllViewResolvers = true;

	/**
	 * Throw a NoHandlerFoundException if no Handler was found to process this request?
	 **/
	private boolean throwExceptionIfNoHandlerFound = false;

	/**
	 * Perform cleanup of request attributes after include request?
	 */
	private boolean cleanupAfterInclude = true;

	/**
	 * MultipartResolver used by this servlet
	 */
	@Nullable
	private MultipartResolver multipartResolver;

	/**
	 * LocaleResolver used by this servlet
	 */
	@Nullable
	private LocaleResolver localeResolver;

	/**
	 * ThemeResolver used by this servlet
	 */
	@Nullable
	private ThemeResolver themeResolver;

	/**
	 * List of HandlerMappings used by this servlet
	 */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/**
	 * List of HandlerAdapters used by this servlet
	 */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/**
	 * List of HandlerExceptionResolvers used by this servlet
	 */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/**
	 * RequestToViewNameTranslator used by this servlet
	 */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/**
	 * FlashMapManager used by this servlet
	 */
	@Nullable
	private FlashMapManager flashMapManager;

	/**
	 * List of ViewResolvers used by this servlet
	 */
	@Nullable
	private List<ViewResolver> viewResolvers;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 *
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 *
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 *
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
		/**
		 * 非常非常重要！！！！！！！！！！！！！！！
		 * SpringMVC的九大组件
		 */
		/**
		 * 	用于处理上传请求
		 *  将普通的Request包装成MultipartHttpServletRequest
		 * 	可以通过getFile() 直接获得文件，如果是多个文件上传，还可以通过调用getFileMap
		 * 	得到Map<FileName,File> 这样的结构。
		 */
		initMultipartResolver(context);
		/**
		 * 在上面我们有看到ViewResolver 的resolveViewName()方法，需要两个参数。那么第
		 * 二个参数Locale是从哪来的呢，这就是LocaleResolver 要做的事了。和国际化相关，可以理解为设置编码
		 */
		initLocaleResolver(context);
		/**
		 * 从名字便可看出，这个类是用来解析主题的。主题，就是样式，图片以及它们所形成的
		 * 显示效果的集合
		 */
		initThemeResolver(context);
		/**
		 *  HandlerMapping是用来查找Handler的，也就是处理器，具体的表现形式可以是类也
		 *  可以是方法。比如，标注了@RequestMapping 的每个 method 都可以看成是一个
		 *  Handler，由 Handler 来负责实际的请求处理。 HandlerMapping 在请求到达之后，
		 *  它的作用便是找到请求相应的处理器Handler和Interceptors。
		 *
		 *  这一步完事后  handlerMappings 中最少存放了2个对象
		 */
		initHandlerMappings(context);
		/**
		 *	从名字上看，这是一个适配器。因为SpringMVC中Handler可以是任意形式的，只要
		 *  能够处理请求便行, 但是把请求交给 Servlet 的时候，就需要根据传递过去的具体类型(方法、类)
		 *  找到对应的 HandlerAdapter然后执行handler的方法
		 */
		initHandlerAdapters(context);
		/**
		 * 从这个组件的名字上看，这个就是用来处理Handler过程中产生的异常情况的组件。 具
		 * 体来说，此组件的作用是根据异常设置ModelAndView, 之后再交给 render()方法进行
		 * 渲 染 ， 而 render() 便 将 ModelAndView 渲 染 成 页 面 。 不 过 有 一 点 ，
		 * HandlerExceptionResolver 只是用于解析对请求做处理阶段产生的异常，而渲染阶段的
		 * 异常则不归他管了，这也是Spring MVC 组件设计的一大原则分工明确互不干涉。 
		 */
		initHandlerExceptionResolvers(context);
		/**
		 *  这个组件的作用，在于从 Request 中获取 viewName. 因为 ViewResolver 是根据
		 * 	ViewName 查找 View, 但有的 Handler 处理完成之后，没有设置 View 也没有设置
		 * 	ViewName， 便要通过这个组件来从Request中查找viewName，就是处理完成之后，跳转到发送request请求的页面
		 */
		initRequestToViewNameTranslator(context);
		/**
		 * 	从方法的定义就可以看出，Controller层返回的String类型的视图名viewName，（ return "xxxx" ）
		 * 	最终会在这里被解析成为View。View 是用来渲染页面的，也就是说，它会将程序返回
		 * 	的参数和数据填入模板中，最终生成 html 文件 直接显示给用户
		 * 	ViewResolver会找到渲染所用的模板（使用什么模板来渲染？）和所用的技术（如JSP）填入参数
		 */
		initViewResolvers(context);
		/**
		 * 用于重定向Redirect时的参数数据传递，比如，在处理用户订单提交时，为
		 * 了避免重复提交，可以处理完post请求后redirect到一个get请求，这个get请求可以
		 * 用来显示订单详情之类的信息。这样做虽然可以规避用户刷新重新提交表单的问题，但
		 * 是在这个页面上要显示订单的信息，那这些数据从哪里去获取呢，因为redirect重定向
		 * 是没有传递参数这一功能的，如果不想把参数写进url(其实也不推荐这么做，url有长度
		 * 限制不说，把参数都直接暴露，感觉也不安全)， 那么就可以通过flashMap来传递。只
		 * 需 要 在 redirect 之 前 ， 将 要 传 递 的 数 据 写 入 request （ 可 以 通 过
		 * ServletRequestAttributes.getRequest() 获 得 ） 的 属 性
		 * OUTPUT_FLASH_MAP_ATTRIBUTE 中，这样在 redirect 之后的 handler 中 Spring 就
		 * 会自动将其设置到Model中，在显示订单信息的页面上，就可以直接从Model 中取得
		 * 数据了。而FlashMapManager就是用来管理FlashMap的。
		 */
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			/**
			 * 想要进行文件上传操作，必须要手动添加一个MultipartResolver实现类，spring自带了分解器
			 * 直接从ioc容器中拿，并且beanName 必须为 multipartResolver
			 */
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using MultipartResolver [" + this.multipartResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			/**
			 * 如果拿不到直接设为空
			 */
			this.multipartResolver = null;
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate MultipartResolver with name '" + MULTIPART_RESOLVER_BEAN_NAME +
						"': no multipart request handling provided");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using LocaleResolver [" + this.localeResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate LocaleResolver with name '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeResolver [" + this.themeResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeResolver with name '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver + "]");
			}
		}
	}

	/**
	 * Initialize the HandlerMappings used by this class.
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 */

	/**
	 * 建立当前ApplicationContext中的所有controller和url的对应关系
	 */
	private void initHandlerMappings(ApplicationContext context) {
		// 初始化记录 HandlerMapping 对象的属性变量为null
		this.handlerMappings = null;

		// 根据属性detectAllHandlerMappings决定是检测所有的 HandlerMapping 对象，还是
		// 使用指定名称的 HandlerMapping 对象
		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			/**
			 * 从容器及其祖先容器查找所有类型为 HandlerMapping 的 HandlerMapping 对象，
			 * spring提供的扩展点，通过实现 requestHandlerMapping类 在注册进spring ioc容器中，
			 * 就可以实现自定义请求类型
			 *
			 * springboot就是通过 import配置类，配置类中 @bean 把扩展的各种handlerMapping、Adapters.....等，
			 * 添加进spring ioc容器，再通过该方法那出来，放到web容器中，子容器
			 */
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			//如果其不为空
			if (!matchingBeans.isEmpty()) {
				//记录到 handlerMappings
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				//根据order属性排序
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		} else {
			try {
				/**
				 *  获取名称为  handlerMapping 的 HandlerMapping bean 并记录到 handlerMappings
				 */
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		/**
		 *  除非你手动扩展handlerMapping，不然从容器是获取不到的
		 * 	如果上面步骤从容器获取 HandlerMapping 失败，则使用缺省策略创建 HandlerMapping 对象记录到 handlerMappings
		 */
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerMappings found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		/**
		 * 整体做法和initHandlerMapping一样
		 *
		 * 在初始化的方法中，将HandlerAdapter对象创建出来保存在DispatcherServlet的handlerAdapters集合中。
		 * 和HandlerMapping一样，同样在初始化的过程中涉及到一个变量detectAllHandlerAdapters，
		 * 可通过设置该值为false来强制系统只加载bean name为“handlerAdapter”的对象。
		 * 如果没有定义HandlerAdapter的话，
		 * Spring MVC就会按照DispatcherServlet.properties所定义的内容来加载默认的HandlerAdapte
		 *
		 */
		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		} else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		if (this.handlerAdapters == null) {
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerAdapters found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		} else {
			try {
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerExceptionResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using RequestToViewNameTranslator [" + this.viewNameTranslator + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate RequestToViewNameTranslator with name '" +
						REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME + "': using default [" + this.viewNameTranslator +
						"]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 */
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			/**
			 * 从spring ioc容器中拿内置的和自定义的视图解析器，通过继承ViewResolver类完成扩展
			 */
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				//排序
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		} else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		if (this.viewResolvers == null) {
			/**
			 * 和初始化handlerMapping一样 从DispatcherServlet.properties中拿
			 */
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No ViewResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using FlashMapManager [" + this.flashMapManager + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate FlashMapManager with name '" +
						FLASH_MAP_MANAGER_BEAN_NAME + "': using default [" + this.flashMapManager + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 *
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 *
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 *
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 *
	 * @param context           the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 *
	 * @param context           the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		// 策略接口长名称作为 key
		String key = strategyInterface.getName();
		/**
		 *
		 *  这里 defaultStrategies 是一个类静态属性，静态代码块给它赋值，指向classpath resource 文件 DispatcherServlet.properties
		 * 	该行获取策略接口对应的实现类,是','分割的实现类的长名称
		 *
		 * 	此时只会拿到 key = org.springframework.web.servlet.HandlerMapping 的 value
		 */
		String value = defaultStrategies.getProperty(key);
		/**
		 * 肯定不为空
		 */
		if (value != null) {
			/**
			 *  按照 ',' 把value(全限定类名)分割成数组
			 */
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			/**
			 * 创建一个集合用来封装返回值
			 */
			List<T> strategies = new ArrayList<>(classNames.length);
			/**
			 * 遍历所有类名
			 */
			for (String className : classNames) {
				try {
					// 获取策略接口实现类
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					// 利用反射创建该策略接口实现类的对象
					Object strategy = createDefaultStrategy(context, clazz);
					/**
					 * 正常情况会有2个对象 
					 */
					strategies.add((T) strategy);
				} catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
									"] for interface [" + key + "]", ex);
				} catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
									className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		} else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 *
	 * @param context the current WebApplicationContext
	 * @param clazz   the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 */
	@Override
	/**
	 * springMVC就是 tomcat 和 用户之间的一个中间处理器
	 *
	 * 分析一下过滤器是如何执行的：
	 * 		过滤器并不是spring负责执行，tomcat负责找出过滤器并且执行 ，tomcat如过没找到过滤器则直接返回
	 * 		如果有，它会找到所有的过滤器，按照责任链的顺序依次执行，如果全部执行完了
	 * 		就会调用HttpService的service方法，最终调用到该方法完成对请求的处理，然后在传回给tomcat，执行别的流程
	 *
	 * 所有的请求都会被该方法自动拦截
	 */
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (logger.isDebugEnabled()) {
			String resumed = WebAsyncUtils.getAsyncManager(request).hasConcurrentResult() ? " resumed" : "";
			logger.debug("DispatcherServlet with name '" + getServletName() + "'" + resumed +
					" processing " + request.getMethod() + " request for [" + getRequestUri(request) + "]");
		}

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		//Spring上下文
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		//国际化解析器
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		//主题解析器
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		//主题
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		if (this.flashMapManager != null) {
			//重定向的数据
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		try {
			/**
			 * 重点！！！
			 */
			doDispatch(request, response);
		} finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	/**
	 * Process the actual dispatching to the handler.
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */

	/**
	 * 实现了对请求的拦截，处理
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		/**
		 * 接受请求
		 */
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				/**
				 * 检查请求是否有文件上传操作
				 */
				processedRequest = checkMultipart(request);
				/**
				 * 如果是文件上传操作，返回的就不是request本身
				 * 经过了封装的request
				 */
				multipartRequestParsed = (processedRequest != request);

				/**
				 * 重点 ！！
				 * 去找Handler的类型，也就是当前请求的是哪种类型的 controller 类or方法
				 * 返回一个HandlerExecutionChain对象
				 * 该对象封装了 handler 和 interceptors.也就是request对应的Controller
				 */
				mappedHandler = getHandler(processedRequest);
				/**
				 * 如果当前请求找不到对应的handler，返回404
				 */
				if (mappedHandler == null) {
					noHandlerFound(processedRequest, response);
					return;
				}
				
				/**
				 * 根据handler不同的类型寻找对应的适配器
				 * 如果是bean getHandler()返回的就是一个对象
				 * 如果是request  getHandler()返回的就是一个方法
				 *
				 * 成功返回一个适配器用来处理 handler
				 */
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				//拿到request请求的头信息(即 POST/GET )
				String method = request.getMethod();
				/**
				 * 如果是GET请求，并且请求信息没有发生改变，直接返回
				 * 看不懂！！！！！！！
				 * 处理 last-modified 请求头
				 */
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (logger.isDebugEnabled()) {
						logger.debug("Last-Modified value for [" + getRequestUri(request) + "] is: " + lastModified);
					}
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}

				/**
				 * 调用执行器链中的拦截器，就是循环装配好的执行器链
				 * 重点是 PreHandle()方法   true代表放行 false代表拦截
				 */
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				/**
				 * 重要！！！！
				 * 根据不同的适配器执行handle
				 * 也就是说该方法执行完毕后，业务逻辑方法就执行完毕了
				 * 接下来要做的就是，通过反射获取该方法上的注解和参数，解析方法和参数上的注解，
				 * 最后反射调用方法获取ModelAndView结果视图
				 * 信息封装到页面渲染展示给用户
				 *
				 * SpringMVC中提供两种request参数到方法中参数的绑定方式：
				 *
				 * 　　① 通过注解进行绑定 @RequestParam
				 *
				 * 　　② 通过参数名称进行绑定
				 *
				 * 使用注解进行绑定，我们只要在方法参数前面声明@RequestParam("a")，
				 * 因为声明了注解，所以spring能保存起来，既而能根据name从request中找
				 * 就可以将request中参数a的值绑定到方法的该参数上。
				 * 使用参数名称进行绑定的前提是必须要获取方法中参数的名称，
				 * Java反射只提供了获取方法的参数的类型，并没有提供获取参数名称的方法。
				 * 也就是说通过反射你只能拿到Class实例中方法的类别，比如说 setName(String name,int id)
				 * 你只能知道 setName(String ,int ) 也就是两个类型，这时候你不可能反射调用
				 * 因为你拿到了值不知道按什么顺序添加进参数中，
				 * SpringMVC解决这个问题的方法是用asm框架直接读取字节码文件(class文件而不是Class实例),
				 * 来获取方法的参数名称。
				 *
				 * 如果是JSON格式不进行渲染
				 */
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
				
				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}
				applyDefaultViewName(processedRequest, mv);
				// 调用拦截器的 postHandle 方法
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			} catch (Exception ex) {
				dispatchException = ex;
			} catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			/**
			 * 最后一步，页面返回给用户
			 * 如果出现异常，返回异常页面。如果没有异常，ModelAndView不为null，
			 * 则正常渲染页面，调用拦截器的afterCompletion方法
			 *
			 * 这一步完成后SpringMVC就基本上完成自己的使命了，当找到对应的url路径，
			 * springmvc就继续访问url对应的servlet，剩下的渲染工作就交由Servlet去处理了，
			 * 所以由此我们不难看出，springmvc实际是在tomcat和servlet中间加了一层，
			 * 处理request将浏览器传递的参数封装成model再由应用来加工处理这些数据后，
			 * 再将这些数据传递给servlet让servlet将这些经过应用处理的数据在页面上表现出来，从而完成web请求，
			 * 目的在于页面和数据分离即页面表现和业务逻辑的分离，
			 * 使开发中可以集中精力处理业务逻辑提高开发效率。
			 */
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		} catch (Exception ex) {
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		} catch (Throwable err) {
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		} finally {
			// 当并发处理时执行的逻辑
			if (asyncManager.isConcurrentHandlingStarted()) {
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			} else {
				// Clean up any resources used by a multipart request.
				// 清理多部分请求使用的所有资源
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		if (mv != null && !mv.hasView()) {
			String defaultViewName = getDefaultViewName(request);
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
									   @Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
									   @Nullable Exception exception) throws Exception {

		boolean errorView = false;
		//如果有异常，则处理异常，返回异常页面
		if (exception != null) {
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			} else {
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				mv = processHandlerException(request, response, handler, exception);
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		/**
		 *  如果ModelAndView不为null
		 */
		if (mv != null && !mv.wasCleared()) {
			/**
			 * 重点！！！
			 *
			 */
			render(mv, request, response);
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Null ModelAndView returned to DispatcherServlet with name '" + getServletName() +
						"': assuming HandlerAdapter completed request handling");
			}
		}

		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}
		//调用处理拦截器的afterCompletion方法
		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 *
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		} else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * <p>If no multipart resolver is set, simply use the existing request.
	 *
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		/**
		 * 先判断是否设置了文件上传解析器
		 * 在判断当前request请求是否是文件上传
		 */
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			/**
			 * 继续判断这个请求是不是已经被转换为MultipartHttpServletRequest类型了。
			 * 在Spring-Web这个jar中有一个过滤器org.springframework.web.multipart.support.MultipartFilter
			 * 如果在web.xml中配置这个过滤器的话，则会在过滤器中提前判断是不是文件上传的请求，
			 * 并将请求转换为MultipartHttpServletRequest类型。
			 */
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				logger.debug("Request is already a MultipartHttpServletRequest - if not in a forward, " +
						"this typically results from an additional MultipartFilter in web.xml");
			} else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution failed for current request before - " +
						"skipping re-resolution for undisturbed error rendering");
			} else {
				try {
					/**
					 * 将请求转换为MultipartHttpServletRequest类型
					 */
					return this.multipartResolver.resolveMultipart(request);
				} catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					} else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		/**
		 * 如果不是直接返回
		 */
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 *
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * Return the HandlerExecutionChain for this request.
	 * <p>Tries all handler mappings in order.
	 *
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		/**
		 * 拿到spring ioc中的所有实现controller和HttpRequestHandler接口的实现类
		 * Spring MVC默认加载两个handlerMapping  按顺序去找，只要找到一个就返回
		 * handlerMappings集合中有两个 1.RequestMappingHandlerMapping
		 * 							  2.BeanNameUrlHandlerMapping
		 */
		if (this.handlerMappings != null) {
			for (HandlerMapping hm : this.handlerMappings) {
				if (logger.isTraceEnabled()) {
					logger.trace(
							"Testing handler map [" + hm + "] in DispatcherServlet with name '" + getServletName() + "'");
				}
				/**
				 * 进行判断是否是请求的当前类型的路径，如果是就执行然后返回结果，如果都不是就返回null
				 */
				HandlerExecutionChain handler = hm.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping found for HTTP request with URI [" + getRequestUri(request) +
					"] in DispatcherServlet with name '" + getServletName() + "'");
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		} else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 *
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			/**
			 *
			 * 遍历适配器
			 * 1.SimpleControllerHandlerAdapter 实现Controller接口的Handler
			 * 2.AbstractHandlerMethodAdapter  RequestMapping注解
			 * 3.HttpRequestHandlerAdapter	  实现HttpRequestHandler接口的Handler
			 * 4.SimpleServletHandlerAdapter  适配Servlet实现类的
			 * 5.AnnotationMethodHandlerAdapter
			 */
			for (HandlerAdapter ha : this.handlerAdapters) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing handler adapter [" + ha + "]");
				}
				if (ha.supports(handler)) {
					return ha;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param handler  the executed handler, or {@code null} if none chosen at the time of the exception
	 *                 (for example, if multipart resolution failed)
	 * @param ex       the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
												   @Nullable Object handler, Exception ex) throws Exception {

		// Check registered HandlerExceptionResolvers...
		ModelAndView exMv = null;
		if (this.handlerExceptionResolvers != null) {
			for (HandlerExceptionResolver handlerExceptionResolver : this.handlerExceptionResolvers) {
				exMv = handlerExceptionResolver.resolveException(request, response, handler, ex);
				if (exMv != null) {
					break;
				}
			}
		}
		if (exMv != null) {
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Handler execution resulted in exception - forwarding to resolved error view: " + exMv, ex);
			}
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 *
	 * @param mv       the ModelAndView to render
	 * @param request  current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception        if there's a problem rendering the view
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 通过解析request的语言环境，并设置response的local
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		View view;
		String viewName = mv.getViewName();
		if (viewName != null) {
			/**
			 * 重点！！！！！
			 * 拿到需要的视图解析器
			 */
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		} else {
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}


		if (logger.isDebugEnabled()) {
			logger.debug("Rendering view [" + view + "] in DispatcherServlet with name '" + getServletName() + "'");
		}
		try {
			//设置响应的状态码
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			/**
			 * 重点！！！！
			 * 开始渲染：调用具体的View对象，进行视图渲染
			 */
			view.render(mv.getModelInternal(), request, response);
		} catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "] in DispatcherServlet with name '" +
						getServletName() + "'", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 *
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 *
	 * @param viewName the name of the view to resolve
	 * @param model    the model to be passed to the view
	 * @param locale   the current locale
	 * @param request  current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 *                   (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
								   Locale locale, HttpServletRequest request) throws Exception {
		/**
		 * 循环遍历所有视图解析器，默认只有一个
		 * springboot中扩展的解析器
		 * 		    1.Thymeleaf
		 *  		2.Freemaker
		 *  		3.JSP
		 * 		    4.Velocity
		 * 		    5.JSON
		 *  		6.XML
		 *  		7.其他
		 */
		if (this.viewResolvers != null) {
			for (ViewResolver viewResolver : this.viewResolvers) {
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					/**
					 * 返回该视图解析器
					 */
					return view;
				}
			}
		}
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
										@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 *
	 * @param request            current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			} else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
