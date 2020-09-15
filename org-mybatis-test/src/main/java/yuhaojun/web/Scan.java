package yuhaojun.web;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Import(MyBeanDefinitionRegister.class)
public @interface Scan {
	String value();
}
