package yuhaojun.di;

import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;

//@Component
public class MyImportAware implements ImportAware {

	String name;

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {

		/**
		 * 	被 importAwarePostProcessor 后置处理器解析
		 *  如果注入的bean实现了 importMetadata 这个接口 那么他就可以在
		 *  注入的时候，拿到某个类上的注解的信息，然后填入属性之中
		 */

		Map<String, Object> annotationAttributes = importMetadata.getAnnotationAttributes("xxx");
		AnnotationAttributes aa = AnnotationAttributes.fromMap(annotationAttributes);
		this.name = (String) aa.get("xxx");

	}
}
