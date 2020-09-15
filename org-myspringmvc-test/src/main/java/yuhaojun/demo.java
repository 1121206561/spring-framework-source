package yuhaojun;

import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import yuhaojun.config.AppConfig;

import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

/**
 * 为什么启动 tomcat 加载该容器能执行该类的onStartup方法
 * 因为 spi规范 和 servlet3.0 以后提供的一种规范
 * 如果你把这个项目交给tomcat管理，tomcat会自动去找jar包下的META-INF/services目录下创建一个以“接口全限定名”为命名的文件
 * 然后执行该类的 onStartup()方法  registration.setLoadOnStartup(1); 设置了加载顺序
 * 最后执行 DispatcherServlet 的 init()方法来进行子容器初始化
 *
 * 重点 spring父子容器
 * 		当一个项目中引入Spring和SpringMVC这两个框架，其实就是2个容器，Spring是父容器，SpringMVC是其子容器，
 * 		子容器可以访问父容器对象，而父容器不可以访问子容器对象
 * 	    我们可以理解为springMVC容器中拥有spring容器的引用，对外暴露的是springMVC的容器
 * 	    而且这两个容器是两个独立的容器互不干涉，拥有着自己独立的属性
 * 	    也就是说我们平常用的所有注解扫描及实例化完成的bean，全都存放在MVC的容器中。所以不会出现问题
 * 	    但是如果把所有的实例化bean放到spring容器中就会报错，因为MVC容器中的单例吃为空找不到controller的bean，更找不到映射
 *
 *      WebApplicationInitializer的实现类中把ContextLoaderListener监听器注册给Tomcat的ServletContext -> contextInitialized()完成了父容器的初始化
 * 		DispatcherServlet 这个类只要被创建一定会执行 init()方法 ，init()方法初始化了一个子容器
 *
 *
 *
 * 在扩展一下springboot是如何启动项目的
 * springboot的启动方式有两种 1.jar 2.war
 * jar包方式的启动  Tomcat t = new Tomcat(80);
 * 					   ...把spring容器存放到tomcat中.....
 * 					t.start();
 * 				也就是说内嵌了一个tomcat，执行了自己的tomcat来启动，
 *
 * 	war包方式启动  启动类实现一个 springApplicationInitializer接口 这个接口又实现了 WebApplicationInitializer 接口
 * 					也是通过SPI规范来放到外部的Tomcat
 */
public class demo implements WebApplicationInitializer {
	@Override
	public void onStartup(javax.servlet.ServletContext servletCxt) throws ServletException {
		/**
		 *  初始化一个Spring的web上下文环境
		 *  springMVC九大组件初始化完毕
		 */
		AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
		//手动注册一个配置类
		ac.register(AppConfig.class);
		//初始化方法
		ac.refresh();

		// Create and register the DispatcherServlet
		/**
		 * DispatcherServlet本质上就如其名字所展示的那样是一个Java Servlet。
		 * 同时它是Spring MVC中最为核心的一块——前端控制器。它主要用来拦截符合要求的外部请求，
		 * 并把请求分发到不同的控制器去处理，根据控制器处理后的结果，生成相应的响应发送到客户端。
		 * DispatcherServlet作为统一访问点，主要进行全局的流程控制
		 */

		/**
		 *  1. 用户向服务器发送请求，请求被Spring 前端控制Servelt DispatcherServlet捕获；
		 *  2. DispatcherServlet对请求URL进行解析，得到请求资源标识符（URI）。然后根据该URI，调用HandlerMapping获得该Handler配置的所有相关的对象（包括Handler对象以及Handler对象对应的拦截器），最后以HandlerExecutionChain对象的形式返回；
		 *  3. DispatcherServlet 根据获得的Handler，选择一个合适的HandlerAdapter。（附注：如果成功获得HandlerAdapter后，此时将开始执行拦截器的preHandler(...)方法）
		 *  4.  提取Request中的模型数据，填充Handler入参，开始执行Handler（Controller)。 在填充Handler的入参过程中，根据你的配置，Spring将帮你做一些额外的工作：
		 *       HttpMessageConveter： 将请求消息（如Json、xml等数据）转换成一个对象，将对象转换为指定的响应信息
		 *       数据转换：对请求消息进行数据转换。如String转换成Integer、Double等
		 *       数据根式化：对请求消息进行数据格式化。 如将字符串转换成格式化数字或格式化日期等
		 *       数据验证： 验证数据的有效性（长度、格式等），验证结果存储到BindingResult或Error中
		 *  5.  Handler执行完成后，向DispatcherServlet 返回一个ModelAndView对象；
		 *  6.  根据返回的ModelAndView，选择一个适合的ViewResolver（必须是已经注册到Spring容器中的ViewResolver)返回给DispatcherServlet ；
		 *  7. ViewResolver 结合Model和View，来渲染视图
		 *  8. 将渲染结果返回给客户端
		 */
		DispatcherServlet servlet = new DispatcherServlet(ac);
		ServletRegistration.Dynamic registration = servletCxt.addServlet("app", servlet);
		registration.setLoadOnStartup(1);
		registration.addMapping("/app/*");
	}
}
