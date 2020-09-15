package yuhaojun.springevent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class TriggerEvent {
	@Autowired
	private ApplicationContext applicationContext;

	//触发事件
	public void TiggerEvent() {
		applicationContext.publishEvent(new MySpringEvent(applicationContext));
	}
}
