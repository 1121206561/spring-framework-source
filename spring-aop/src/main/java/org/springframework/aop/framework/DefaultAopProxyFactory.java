/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.aop.SpringProxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	/**
	 * spring中的两个代理类   JdkDynamicAopProxy   ObjenesisCglibAopProxy
	 *  都实现了invoke 最重要的方法 实现AOP中具体的逻辑；也就是如何对代理类进行增强
	 *  该方法太难了 能力有限 看不懂  大体就是获取 当前方法的拦截器链chain；
	 *											   如果chain为空，直接调用切点方法；
	 * 										       chain不为空，将拦截器封装在ReflectiveMethodInvocation 然后循环调用
	 * 										       最后再去 调用切点对每个方法怎强
	 * 										       	仅 个人理解！！！！！！
	 */
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		/**
		 * 第一个判断:是否需要优化,是专门针对于CGlib代理的优化策略，并不推荐属于此配置
		 * 检测 proxyTargetClass 的值  可以通过程序员手动设置来改变值 强行使用CGLIB
		 * 重点是最后一个判断 见名知意判断是否有接口代理
		 * 如果为true 使用 cglib动态代理  否则使用jdk动态代理
		 *
		 * 	总结
		 * 	如果目标对象实现了接口，则优先使用 JDK 代理
		 *  如果目标对象实现了接口，可以设置proxy-target-class属性为true，强制使用CgLib代理
		 *  如果目标对象没有实现接口，则必须使用 CGLIB 进行代理
		 */
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}

			//就算强制使用Cglib 在特殊情况下依然还是用jdk代理
			//目标类是一个接口 || 目标类是一个代理类
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}
			//返回一个代理器
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			//返回一个代理器
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		/**
		 * 判定一个当前Class对象表示的类或者接口与传入的Class参数相比，是否相同，或者
		 * 是否是它的一个超类或者超接口。是的话返回true，否则就返回false。当这个Class
		 * 对象表示一种原始类型时，如果传入的Class对象参数就是这个Class对象，那么返回
		 * 真，否则为假。
		 */
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
