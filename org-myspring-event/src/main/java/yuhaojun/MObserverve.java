package yuhaojun;

import java.util.Observable;

/**
 * JDK中的被观察者
 */
public class MObserverve extends Observable {

	public void excu() {
		//设置状态值改变
		this.setChanged();
		//执行所有观察者的update方法
		notifyObservers();
	}
}
