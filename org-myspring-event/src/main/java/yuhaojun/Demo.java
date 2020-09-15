package yuhaojun;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import yuhaojun.config.AppConfig;
import yuhaojun.springevent.TriggerEvent;

public class Demo {

	/**
	 * 第一阶段：观察者中引用了被观察者，如果被观察者发生了改变就执行相应的行为，太耗CPU因为要一直循环
	 * 第二阶段：被观察者中引用了观察者，减少了CPU的损耗，但是事件这一概念消失了
	 * 第三阶段：把事件抽象出来封装成一个类，事件类保存了什么时候执行什么方法，观察者通过接收被观察者传递过来的事件，然后执行
	 * 第四阶段：不可能只有一个观察者，所以被观察者者保存一个集合，用来保存观察者
	 * 第五阶段：观察者有着无数种类型，所以需要定义一个接口，观察者实现接口，来统一类型便于存入集合
	 * <p>
	 * 特点：1.被观察者持有监听的观察者的引用
	 * 2.被观察者支持增加和删除观察者
	 * 3.被观察者改变状态，通知观察者
	 * <p>
	 * 优点松耦合
	 * 1.观察者增加或删除无需改变被观察者的代码，只需要调用被观察者的增加或删除方法
	 * 2.被观察者无需知道观察者如何处理，只需通知即可
	 * 3.观察者只需等待通知，无需知道被观察者的代码
	 * <p>
	 * <p>
	 * 开关的重要性：无需改变代码，就可以实现选择是否通知
	 */
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
		ac.getBean(TriggerEvent.class).TiggerEvent();
		MObserverve mObserverve = new MObserverve();
		mObserverve.addObserver(new MObserver());
		mObserverve.excu();
	}
}
