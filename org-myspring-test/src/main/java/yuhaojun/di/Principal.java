package yuhaojun.di;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Principal {

	@Autowired
	private Teacher teacher;
}
