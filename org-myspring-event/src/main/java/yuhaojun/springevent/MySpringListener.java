package yuhaojun.springevent;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 自定义一个监听器专门用来接收MySpringEvent事件
 * 只要触发了一次 MySpringEvent 这个事件，就会执行方法
 *
 * 我们需要了解源码才懂：1.为什么这个监听器会注册到spring容器里
 * 						 2.如何让不同的监听器监听不同的事件
 * 						 3.怎么触发的监听事件
 */
@Component
public class MySpringListener implements ApplicationListener<MySpringEvent> {

	@Override
	public void onApplicationEvent(MySpringEvent event) {
		System.out.println("触发自定义事件");
	}
}
