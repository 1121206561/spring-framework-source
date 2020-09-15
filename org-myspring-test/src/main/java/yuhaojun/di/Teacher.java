package yuhaojun.di;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Teacher implements FactoryBean {

	@Override
	public Object getObject() throws Exception {
		return new Worker();
	}

	@Override
	public Class<?> getObjectType() {
		return null;
	}
}
