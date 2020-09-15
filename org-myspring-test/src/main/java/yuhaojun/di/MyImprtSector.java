package yuhaojun.di;

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

/**
 * ImportSelector强调的是复用性，使用它需要自建一个类，这里建的是MyImportSelector，
 * 然后继承ImportSelector接口实现方法，方法的返回值是字符串数组，也就是组件们得全类名。。直接上代码
 * 通过 @import(MyImprtSector.class) 实现注入
 */

public class MyImprtSector implements ImportSelector {

	//实现了ImportSelector注解
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[]{"xxx.class.getName()","xxx.class.getName()","xxx.class.getName()"};
	}
}
