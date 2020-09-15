package yuhaojun;

import java.util.Observable;
import java.util.Observer;

/**
 * JDK中提供的观察者模式的接口
 */
public class MObserver implements Observer {
	/**
	 * 执行方法
	 */
	@Override
	public void update(Observable o, Object arg) {
		System.out.println();
	}
}
