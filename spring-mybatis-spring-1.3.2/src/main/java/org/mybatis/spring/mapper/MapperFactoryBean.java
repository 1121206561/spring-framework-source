/**
 *    Copyright 2010-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a
 * SqlSessionFactory or a pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

	/**
	 * 使用BY_TYPE类型，先拿到所有的属性，如果有get/set方法就证明该属性需要被注入
	 * 但是会经过过滤，过滤掉某些属性  1.Class类型 2.显示赋值了
	 */
  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    //intentionally empty 
  }

	/**
	 *  构造器，此时beanDefinition中已经设置了构造器输入参数
	 * 	所以在通过反射调用构造器实例化时，一定会通过该构造方法去实例化对象
	 * 	并且获取在BeanDefinition设置的构造器输入参数
	 * 	也就是对应得每个Mapper接口Class(com.mapper.xxMapper)赋给mapperInterface属性
	 */
  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   *  重点！！！！！
   *  因为该类实现了 InitializingBean 接口，会在 属性注入(@Autowired)之后、执行afterPropertiesSet 方法
   *  afterPropertiesSet方法执行了 checkDaoConfig 方法
   *  为什么要在属性注入之后才建立 映射关系
   *  因为当属性注入完成之后，该代理对象才完整，才能实现映射
   *
   */
  @Override
  protected void checkDaoConfig() {
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
		  /**
		   * 重点！！！
		   * 往 MappedStatement 中添加了全限定名+方法名作为key，sqlSource作为value代理对象
		   */
		  configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        //logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T getObject() throws Exception {
	  /**
	   * 最重要的点！！！！！！！没有之一
	   * 为什么接口会变成一个动态代理对象？这个方法就做了这件事
	   *
	   *  获取父类setSqlSessionFactory方法中创建的 SqlSessionTemplate
	   *  通过SqlSessionTemplate获取mapperInterface的代理类
	   *  重点！！(只有尝试从单例池中拿该bean的时候才会执行该方法) 只有调用该方法获取到Mapper接口的代理类后，才会存放到factoryBeanCache中
	   *  也就是说不会在单例池中存在，singletonObjects只存在MapperFactoryBean这个类，不存在Mapper接口的代理类
	   *  所以说该方法只会执行一次，除非清除掉缓存，才会再次执行
	   */
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  //------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means
   * it must have been included in mybatis-config.xml.
   * <p/>
   * If it is true, the mapper will be added to MyBatis in the case it is not already
   * registered.
   * <p/>
   * By default addToCofig is true.
   *
   * @param addToConfig
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already
   * registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
