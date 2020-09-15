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
package org.mybatis.spring;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.apache.ibatis.reflection.ExceptionUtil.unwrapThrowable;
import static org.mybatis.spring.SqlSessionUtils.closeSqlSession;
import static org.mybatis.spring.SqlSessionUtils.getSqlSession;
import static org.mybatis.spring.SqlSessionUtils.isSqlSessionTransactional;
import static org.springframework.util.Assert.notNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * Thread safe, Spring managed, {@code SqlSession} that works with Spring
 * transaction management to ensure that that the actual SqlSession used is the
 * one associated with the current Spring transaction. In addition, it manages
 * the session life-cycle, including closing, committing or rolling back the
 * session as necessary based on the Spring transaction configuration.
 * <p>
 * The template needs a SqlSessionFactory to create SqlSessions, passed as a
 * constructor argument. It also can be constructed indicating the executor type
 * to be used, if not, the default executor type, defined in the session factory
 * will be used.
 * <p>
 * This template converts MyBatis PersistenceExceptions into unchecked
 * DataAccessExceptions, using, by default, a {@code MyBatisExceptionTranslator}.
 * <p>
 * Because SqlSessionTemplate is thread safe, a single instance can be shared
 * by all DAOs; there should also be a small memory savings by doing this. This
 * pattern can be used in Spring configuration files as follows:
 *
 * <pre class="code">
 * {@code
 * <bean id="sqlSessionTemplate" class="org.mybatis.spring.SqlSessionTemplate">
 *   <constructor-arg ref="sqlSessionFactory" />
 * </bean>
 * }
 * </pre>
 *
 * @author Putthibong Boonbong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see SqlSessionFactory
 * @see MyBatisExceptionTranslator
 */

/**
 *  因为真正代理的类是  SqlSessionTemplate 所以他有着实现类的所有方法，并且其中还保存着 xxMapper接口的全限定类名
 *  我们看到SqlSessionTemplate实现了SqlSession接口，那么Mapper代理类中执行所有的数据库操作，
 *  都是通过SqlSessionTemplate来执行，
 *  如上我们看到所有的数据库操作都由对象sqlSessionProxy执行查询
 *
 */
public class SqlSessionTemplate implements SqlSession, DisposableBean {

  private final SqlSessionFactory sqlSessionFactory;

  private final ExecutorType executorType;

  private final SqlSession sqlSessionProxy;

  private final PersistenceExceptionTranslator exceptionTranslator;

  /**
   * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory}
   * provided as an argument.
   *
   * @param sqlSessionFactory
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    this(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType());
  }

  /**
   * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory}
   * provided as an argument and the given {@code ExecutorType}
   * {@code ExecutorType} cannot be changed once the {@code SqlSessionTemplate}
   * is constructed.
   *
   * @param sqlSessionFactory
   * @param executorType
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType) {
    this(sqlSessionFactory, executorType,
        new MyBatisExceptionTranslator(
            sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), true));
  }

  /**
   * Constructs a Spring managed {@code SqlSession} with the given
   * {@code SqlSessionFactory} and {@code ExecutorType}.
   * A custom {@code SQLExceptionTranslator} can be provided as an
   * argument so any {@code PersistenceException} thrown by MyBatis
   * can be custom translated to a {@code RuntimeException}
   * The {@code SQLExceptionTranslator} can also be null and thus no
   * exception translation will be done and MyBatis exceptions will be
   * thrown
   *
   * @param sqlSessionFactory
   * @param executorType
   * @param exceptionTranslator
   */

	/**
	 * SqlSessionTemplate在内部访问数据库时，其实是委派给sqlSessionProxy来执行数据库操作的，
	 * SqlSessionTemplate不是自身重新实现了一套mybatis数据库访问的逻辑。
	 *
	 * SqlSessionTemplate通过静态代理机制来提供SqlSession接口的行为，
	 * 即实现SqlSession接口来获取SqlSession的所有方法；SqlSessionTemplate的定义如下：标准的静态代理实现模式，
	 * 即实现SqlSession接口并在内部包含一个SqlSession接口实现类引用sqlSessionProxy。
	 */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sqlSessionFactory, "Property 'sqlSessionFactory' is required");
    notNull(executorType, "Property 'executorType' is required");

    this.sqlSessionFactory = sqlSessionFactory;
    this.executorType = executorType;
    this.exceptionTranslator = exceptionTranslator;
	  /**
	   * 重点！！！！！
	   */
    this.sqlSessionProxy = (SqlSession) newProxyInstance(
        SqlSessionFactory.class.getClassLoader(),
        new Class[] { SqlSession.class },
        new SqlSessionInterceptor());
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return this.sqlSessionFactory;
  }

  public ExecutorType getExecutorType() {
    return this.executorType;
  }

  public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
    return this.exceptionTranslator;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T selectOne(String statement) {
    return this.sqlSessionProxy.<T> selectOne(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T 	selectOne(String statement, Object parameter) {
	  /**
	   * 所以他会通过sqlSessionProxy来执行sql语句，
	   * 又因为这个sqlSessionProxy依然是一个代理对象，所以继续看他的 invoke()
	   */
    return this.sqlSessionProxy.<T> selectOne(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.sqlSessionProxy.<K, V> selectMap(statement, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return this.sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey, rowBounds);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement) {
    return this.sqlSessionProxy.<E> selectList(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.sqlSessionProxy.<E> selectList(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return this.sqlSessionProxy.<E> selectList(statement, parameter, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, rowBounds, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement) {
    return this.sqlSessionProxy.insert(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement, Object parameter) {
    return this.sqlSessionProxy.insert(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement) {
    return this.sqlSessionProxy.update(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement, Object parameter) {
    return this.sqlSessionProxy.update(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement) {
    return this.sqlSessionProxy.delete(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement, Object parameter) {
    return this.sqlSessionProxy.delete(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getMapper(Class<T> type) {
	  /**
	   *  通过sqlSessionFactory创建代理类，sqlSessionTemplate作为代理类的模板，并且该类还有一个内部类
	   *  InvocationHandler()，作为对每个方法的增强，通过拿到方法上的注解 进行具体的执行操作
	   *
	   * 实现SqlSession接口，单例、线程安全，使用spring的事务管理器的sqlSession，
	   * 具体的SqlSession的功能，则是通过内部包含的sqlSessionProxy来来实现，这也是静态代理的一种实现。
	   * 同时内部的sqlSessionProxy实现InvocationHandler接口，则是动态代理的一种实现，而线程安全也是在这里实现的。
	   * 注意mybatis默认的sqlSession不是线程安全的，需要每个线程有一个单例的对象实例。
	   * SqlSession的主要作用是提供SQL操作的API，执行指定的SQL语句，mapper需要依赖SqlSession来执行其方法对应的SQL
	   *
	   * Configuration就像是Mybatis的总管，Mybatis的所有配置信息都存放在这里
	   * 比如：结果集处理器，是否开启缓存，驼峰表示法，slf4j，各种缓存等等
	   * 此外，它还提供了设置这些配置信息的方法。
	   * Configuration可以从配置文件里获取属性值，也可以通过程序直接设置。
	   *
	   * 他把自己传进去了重点
	   */
    return getConfiguration().getMapper(type, this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit() {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit(boolean force) {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback() {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback(boolean force) {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    throw new UnsupportedOperationException("Manual close is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clearCache() {
    this.sqlSessionProxy.clearCache();
  }

  /**
   * {@inheritDoc}
   *
   */
  @Override
  public Configuration getConfiguration() {
    return this.sqlSessionFactory.getConfiguration();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Connection getConnection() {
    return this.sqlSessionProxy.getConnection();
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.0.2
   *
   */
  @Override
  public List<BatchResult> flushStatements() {
    return this.sqlSessionProxy.flushStatements();
  }

  /**
  * Allow gently dispose bean:
  * <pre>
  * {@code
  *
  * <bean id="sqlSession" class="org.mybatis.spring.SqlSessionTemplate">
  *  <constructor-arg index="0" ref="sqlSessionFactory" />
  * </bean>
  * }
  *</pre>
  *
  * The implementation of {@link DisposableBean} forces spring context to use {@link DisposableBean#destroy()} method instead of {@link SqlSessionTemplate#close()} to shutdown gently.
  *
  * @see SqlSessionTemplate#close()
  * @see org.springframework.beans.factory.support.DisposableBeanAdapter#inferDestroyMethodIfNecessary
  * @see org.springframework.beans.factory.support.DisposableBeanAdapter#CLOSE_METHOD_NAME
  */
  @Override
  public void destroy() throws Exception {
  //This method forces spring disposer to avoid call of SqlSessionTemplate.close() which gives UnsupportedOperationException
  }

    /**
   * Proxy needed to route MyBatis method calls to the proper SqlSession got
   * from Spring's Transaction Manager
   * It also unwraps exceptions thrown by {@code Method#invoke(Object, Object...)} to
   * pass a {@code PersistenceException} to the {@code PersistenceExceptionTranslator}.
   */
  private class SqlSessionInterceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		/**
		 * 开启 sqlSession，
		 * 获取一个sqlSession来执行proxy的method对应的SQL,
		 * 每次调用都获取创建一个sqlSession线程局部变量，故不同线程相互不影响，
		 * 在这里实现了SqlSessionTemplate的线程安全性
		 *
		 */
		SqlSession sqlSession = getSqlSession(
          SqlSessionTemplate.this.sqlSessionFactory,
          SqlSessionTemplate.this.executorType,
          SqlSessionTemplate.this.exceptionTranslator);
      try {
		  /**
		   *  直接通过新创建的SqlSession反射调用method
		   *  这也就解释了为什么不需要目标类属性了，这里每次都会创建一个
		   */
		  Object result = method.invoke(sqlSession, args);
        if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
			/**
			 *   如果当前操作没有在一个Spring事务中，则手动commit一下
			 *   如果当前业务没有使用@Transation,那么每次执行了Mapper接口的方法直接commit
			 *   还记得我们前面讲的Mybatis的一级缓存吗，这里一级缓存不能起作用了，
			 *   因为每执行一个Mapper的方法，sqlSession都提交了
			 *   sqlSession提交，会清空一级缓存
			 */
			sqlSession.commit(true);
        }
        return result;
      } catch (Throwable t) {
        Throwable unwrapped = unwrapThrowable(t);
        if (SqlSessionTemplate.this.exceptionTranslator != null && unwrapped instanceof PersistenceException) {
          // release the connection to avoid a deadlock if the translator is no loaded. See issue #22
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
          sqlSession = null;
          Throwable translated = SqlSessionTemplate.this.exceptionTranslator.translateExceptionIfPossible((PersistenceException) unwrapped);
          if (translated != null) {
            unwrapped = translated;
          }
        }
        throw unwrapped;
      } finally {
        if (sqlSession != null) {
			/**
			 * session关闭，所以session是交给了spring管理的
			 * 我们手动开关session是没什么作用的。这样一级缓存就失效了
			 */
			closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
        }
      }
    }
  }

}
