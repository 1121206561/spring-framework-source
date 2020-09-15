package yuhaojun.config;

import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 *  注解表示当前配置类是一个web配置类，如果不加上这个注解，该配置类不会被mvc识别
 */
@EnableWebMvc
@Configuration
public class SpringAndSpringMVC implements WebMvcConfigurer {
	/**
	 * 如果依然还是按照demo的方式启动springMVC容器会报错  ；NO ServletContext set
	 * 但是如果你把 ac.refresh();去掉完美解决
	 *  why？
	 *  父子容器！！！
	 *  
	 */
	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		/**
		 * 如果你想 MVC 能够识别返回的各种类型的JSON格式
		 * 必须给 MVC 中处理json的处理方法手动添加一个解析器
		 */
		FastJsonHttpMessageConverter jc = new FastJsonHttpMessageConverter();
		converters.add(jc);
	}
}
