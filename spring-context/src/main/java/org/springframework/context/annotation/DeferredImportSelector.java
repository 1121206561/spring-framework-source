/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.context.annotation;

/**
 * A variation of {@link ImportSelector} that runs after all {@code @Configuration} beans
 * have been processed. This type of selector can be particularly useful when the selected
 * imports are {@code @Conditional}.
 *
 * <p>Implementations can also extend the {@link org.springframework.core.Ordered}
 * interface or use the {@link org.springframework.core.annotation.Order} annotation to
 * indicate a precedence against other {@link DeferredImportSelector}s.
 *
 * @author Phillip Webb
 * @since 4.0
 *
 *
 *  ImportSelector的子接口  延时解析 Configuration 注解中的其他逻辑被处理之前，所谓的其他逻辑，包括对@ImportResource、@Bean这些注解的处理
 *  （注意，这里只是对@Bean修饰的方法的处理，并不是立即调用@Bean修饰的方法，这个区别很重要！）；
 */
public interface DeferredImportSelector extends ImportSelector {

}
