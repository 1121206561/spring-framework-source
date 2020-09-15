package yuhaojun.di;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
public class A implements FactoryBean {
	@Override
	public Object getObject() throws Exception {
		return new B();
	}

	@Override
	public Class<?> getObjectType() {
		return null;
	}
}
