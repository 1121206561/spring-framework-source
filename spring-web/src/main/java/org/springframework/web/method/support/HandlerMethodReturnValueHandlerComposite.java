/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Handles method return values by delegating to a list of registered {@link HandlerMethodReturnValueHandler}s.
 * Previously resolved return types are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();


	/**
	 * Return a read-only list with the registered handlers, or an empty list.
	 */
	public List<HandlerMethodReturnValueHandler> getHandlers() {
		return Collections.unmodifiableList(this.returnValueHandlers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method return type} is supported by any registered
	 * {@link HandlerMethodReturnValueHandler}.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return getReturnValueHandler(returnType) != null;
	}

	@Nullable
	private HandlerMethodReturnValueHandler getReturnValueHandler(MethodParameter returnType) {
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;
	}

	/**
	 * Iterate over registered {@link HandlerMethodReturnValueHandler}s and invoke the one that supports it.
	 * @throws IllegalStateException if no suitable {@link HandlerMethodReturnValueHandler} is found.
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		/**
		 *  拿到返回结果处理程序
 		 */
		HandlerMethodReturnValueHandler handler = selectHandler(returnValue, returnType);
		/**
		 * 如果没有处理程序匹配，就报错！！！！！！！
		 */
		if (handler == null) {
			throw new IllegalArgumentException("Unknown return value type: " + returnType.getParameterType().getName());
		}
		/**
		 *  通过处理程序对返回结果进行处理
		 *  我们通过返回 Json格式的数据 来进行学习
		 *  RequestResponseBodyMethodProcessor
		 */
		handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
	}

	@Nullable
	private HandlerMethodReturnValueHandler selectHandler(@Nullable Object value, MethodParameter returnType) {
		// 判断是否为异步返回值
		boolean isAsyncValue = isAsyncReturnValue(value, returnType);
		/**
		 * spring当中维护了一个map，专门用来存放，处理结果的所有处理程序，它们都是一个类
		 * 循环所有处理程序
		 */
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			/**
			 * 如果它是异步返回值，那么只要处理器不是异步处理程序直接循环下一个
			 */
			if (isAsyncValue && !(handler instanceof AsyncHandlerMethodReturnValueHandler)) {
				continue;
			}

			/**
			 * 重要！！！！！
			 * 观察者模式体现的淋漓尽致。。。
			 *  spring会先对所有处理程序进行排序。
			 *  按照顺序执行所有处理程序的 supportsReturnType() 方法，只要有一个返回true
			 *  那么就认为找到了处理程序，把该处理器返回
			 *
			 *  最简单的情况即我们只返回了一个视图名称，此时返回 ViewNameMethodReturnValueHandler
 			 */
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;
	}

	private boolean isAsyncReturnValue(@Nullable Object value, MethodParameter returnType) {
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler instanceof AsyncHandlerMethodReturnValueHandler) {
				if (((AsyncHandlerMethodReturnValueHandler) handler).isAsyncReturnValue(value, returnType)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandler(HandlerMethodReturnValueHandler handler) {
		this.returnValueHandlers.add(handler);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}s.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandlers(
			@Nullable List<? extends HandlerMethodReturnValueHandler> handlers) {

		if (handlers != null) {
			for (HandlerMethodReturnValueHandler handler : handlers) {
				this.returnValueHandlers.add(handler);
			}
		}
		return this;
	}

}
