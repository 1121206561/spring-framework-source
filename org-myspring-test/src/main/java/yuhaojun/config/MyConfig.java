package yuhaojun.config;


import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import yuhaojun.di.*;

@EnableAspectJAutoProxy //如果想要让spring支持AspectJ这种语法 必须要先在配置类中开启
@ComponentScan("yuhaojun.di")
@Configuration
@EnableTransactionManagement
@Import({MyImportAware.class, MyImportRegisrerbeanDefinition.class})
public class MyConfig {

	//@Bean
	public Principal setPrincipal() {
		//cglib代理配置类，多次创建的场景
		setTeacher();
		return new Principal();
	}

	//@Bean
	public Teacher setTeacher() {
		return new Teacher();
	}
}
