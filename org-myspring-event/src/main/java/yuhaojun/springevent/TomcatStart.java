package yuhaojun.springevent;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import yuhaojun.config.AppConfig;

import java.io.File;

public class TomcatStart {
	/**
	 * 先初始化spring ioc容器
	 * 在启动tomcat
	 * 在手动创建一个servlet，把ioc容器当作属性传进去
	 * 再把servlet注册给tomcat
	 * 最后启动tomcat
	 */
	public static void main(String[] args) throws LifecycleException {

		AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
		ac.register(AppConfig.class);
		ac.refresh();

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);
		File base = new File(System.getProperty("java.io.tmpdir"));
		Context root = tomcat.addContext("/", base.getAbsolutePath());

		DispatcherServlet dispatcherServlet = new DispatcherServlet(ac);
		tomcat.addServlet(root, "Luban", dispatcherServlet).setLoadOnStartup(0);
		root.addServletMappingDecoded("/", "Luban");
		tomcat.start();
		tomcat.getServer().await();

	}
}
