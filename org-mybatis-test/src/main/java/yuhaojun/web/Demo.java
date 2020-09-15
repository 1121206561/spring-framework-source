package yuhaojun.web;

import yuhaojun.mapper.TDao;

import java.lang.reflect.Proxy;

public class Demo {
	public static Object getMapper(Class<TDao> tDaoClass) {
		/**
		 * 通过动态代理得到代理对象
		 */
		Class<?>[] interfaces = {tDaoClass};
		//成功创建对象并返回
		Object o = Proxy.newProxyInstance(tDaoClass.getClassLoader(), interfaces, new MyInvocationHandler());
		return o;
	}
}
