package yuhaojun;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import yuhaojun.config.AppConfig;
import yuhaojun.mapper.TDao;
import yuhaojun.service.TService;
import yuhaojun.web.Demo;

public class test {
	/**
	 * 这里大概讲一下Mapper代理类调用方法执行逻辑：
	 *
	 * 1、SqlSessionTemplate生成Mapper代理类时，将本身传进去做为Mapper代理类的属性，调用Mapper代理类的方法时，
	 * 最后会通过SqlSession类执行，也就是调用SqlSessionTemplate中的方法。
	 *
	 * 2、SqlSessionTemplate中操作数据库的方法中又交给了sqlSessionProxy这个代理类去执行，
	 * 那么每次执行的方法都会回调其SqlSessionInterceptor这个InvocationHandler的invoke方法
	 *
	 * 3、在invoke方法中，为每个线程创建一个新的SqlSession，并通过反射调用SqlSession的method。
	 * 这里sqlSession是一个线程局部变量，不同线程相互不影响，实现了SqlSessionTemplate的线程安全性
	 *
	 * 4、如果当前操作并没有在Spring事务中，那么每次执行一个方法，都会提交，相当于数据库的事务自动提交，
	 * Mysql的一级缓存也将不可用
	 */
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
		ac.getBean(TService.class).print();
		System.out.println(ac.getBean("&myFactoryBean"));

		/**    下面的过程就是如何单独的使用mybatis的步骤
		 * 			等到学mybatis再去深究
		 *
		 * 		DataSource dataSource = BlogDataSourceFactory.getBlogDataSource();
		 * 		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		 * 		Environment environment = new Environment("development", transactionFactory, dataSource);
		 * 		Configuration configuration = new Configuration(environment);
		 * 		configuration.addMapper(BlogMapper.class);
		 * 		SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		 */

		//假设我们通过配置文件 得到了一个 SqlSession工厂
		SqlSessionFactory sqlSessionFactory = null;
		SqlSession sqlSession = sqlSessionFactory.openSession();
		//可以看出他是通过动态代理拿到的 mapper 对象
		TDao tDao = sqlSession.getMapper(TDao.class);
		tDao.list();

		/**
		 * 手动模拟一个 mybatis的动态代理
		 */
		//传递接口得到对象
		TDao MyTDao = (TDao) Demo.getMapper(TDao.class);
		MyTDao.list();

		/**
		 * mybatis和spring整合后一级缓存失效的问题
		 *
		 * 一级缓存: MyBatis会创建出一个SqlSession对象表示一次数据库会话。
		 * 			在对数据库的一次会话中，我们有可能会反复地执行完全相同的查询语句，
		 * 			由于查询一次数据库的代价很大，这有可能造成很大的资源浪费。
		 *			为了解决这一问题，减少资源的浪费，MyBatis会在表示会话的SqlSession对象中建立一个简单的缓存，
		 *			将每次查询到的结果结果缓存起来，当下次查询的时候，如果判断先前有个完全一样的查询，
		 *			会直接从缓存中直接将结果取出，返回给用户，不需要再进行一次数据库查询了，
		 *			当sqlSession关闭的时候就会清除缓存
		 *
		 *	为什么spring-mybatis会手动关闭一级缓存
		 *		1.spring把所有的bean都放在spring ioc容器中，他并没有提供给用户 applicationContext
		 *		  所以用户无法拿到Mapper对象，更拿不到sqlSession，也就无法做到手动关闭
		 *		  mybatis提供了api使用户能随时关闭
		 *		2.mybatis的一级缓存很鸡肋，因为一级缓存是存在每个线程中，每次的请求都是一个新的线程
		 *		  那么这个一级缓存可以说是完全没有用，但是如果他是进程级别的，就很牛b了，能节省大量的时间
		 */
		TService service = ac.getBean(TService.class);
		/**
		 *  和spring整合后进行数据查询，会对方法做动态代理，
		 *  所以直接看动态代理的invoke()方法做了什么，动态代理使用的是 sqlSessionTemp这个类
		 *  该类属于mybatis-spring的jar包独有类，所以和 mybatis没什么关系
		 *  这也证实了我们修改mybatis的配置并不能对sqlSession起作用的原因
		 *  执行完自己的invoke()方法 -> sqlSessionProxy(sql)这个对象依然是代理对象 ->  invoke()
		 *  ->开启sqlSession ->  执行sql  ->  关闭sqlSession
		 *  所以他并没有一级缓存
		 */
		service.tDao.list();
		/**
		 * 单纯的mybatis进行数据查询
		 * 大部分操作都和上面一样
		 * 但是它使用的代理类是  defSqlSession  是mybatis中的类
		 * 他和spring的代理类的invoke方法有区别
		 * 该类是直接执行的sql语句，并没有使用代理类执行语句
		 * 并且不会关闭sqlSession，即不会清楚缓存，只有手动配置了关闭一级缓存才会清除
		 */
		new TService().tDao.list();
	}
}
