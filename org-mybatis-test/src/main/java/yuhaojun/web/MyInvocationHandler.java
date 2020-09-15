package yuhaojun.web;

import org.apache.ibatis.annotations.Select;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MyInvocationHandler implements InvocationHandler {
	//代理对象

	/**
	 * 因为该方法会在每个方法都会调用一遍
	 * 所以传递过来的method 就等于每个方法上的注解是不同的
	 */
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		//得到方法上的 @select 上的注解信息
		Select annotation = method.getAnnotation(Select.class);
		System.out.println(annotation.value()[0]);
		System.out.println("-------------------------");
		return null;
	}
}
