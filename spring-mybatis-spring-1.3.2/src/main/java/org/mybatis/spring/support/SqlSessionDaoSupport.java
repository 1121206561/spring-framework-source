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
package org.mybatis.spring.support;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.support.DaoSupport;

/**
 * Convenient super class for MyBatis SqlSession data access objects.
 * It gives you access to the template which can then be used to execute SQL methods.
 * <p>
 * This class needs a SqlSessionTemplate or a SqlSessionFactory.
 * If both are set the SqlSessionFactory will be ignored.
 * <p>
 * {code Autowired} was removed from setSqlSessionTemplate and setSqlSessionFactory
 * in version 1.2.0.
 * 
 * @author Putthibong Boonbong
 *
 * @see #setSqlSessionFactory
 * @see #setSqlSessionTemplate
 * @see SqlSessionTemplate
 */
public abstract class SqlSessionDaoSupport extends DaoSupport {

  private SqlSession sqlSession;

  private boolean externalSqlSession;

	/**
	 * 因为是By_Type注入模型，所以会调用所有setXX()方法
	 */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    if (!this.externalSqlSession) {
      this.sqlSession = new SqlSessionTemplate(sqlSessionFactory);
    }
  }

  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSession = sqlSessionTemplate;
    this.externalSqlSession = true;
  }

  /**
   * Users should use this method to get a SqlSession to call its statement methods
   * This is SqlSession is managed by spring. Users should not commit/rollback/close it
   * because it will be automatically done.
   *
   * @return Spring managed thread safe SqlSession
   */
  public SqlSession getSqlSession() {
	  /**
	   * 此时这个值是存在的，因为我们对beanDefinition设置了sqlSessionFactory属性
	   * 在实例化后的属性注入时，通过setSqlSessionFactory() 设值 sqlSessionTemplate
	   * sqlSessionTemplate对象也是一个单例，全局唯一，供所有的Mapper代理类使用
	   */
    return this.sqlSession;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkDaoConfig() {
    notNull(this.sqlSession, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
  }

}
