package yuhaojun.springevent;

import org.springframework.context.ApplicationEvent;

/**
 * 自定义了一个spring事件
 */
public class MySpringEvent extends ApplicationEvent {

	public MySpringEvent(Object source) {
		super(source);
	}
}
