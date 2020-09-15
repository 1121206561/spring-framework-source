package yuhaojun.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import yuhaojun.mapper.TDao;
import yuhaojun.web.MyBeanDefinitionRegister;
import yuhaojun.web.Scan;

import javax.sql.DataSource;

@MapperScan("yuhaojun.mapper")  //通过扫描的方式自动注入  可以扫描多个
@ComponentScan("yuhaojun")
@Configuration
@Scan("yuhaojun.mapper.TDao")
@Import(MyBeanDefinitionRegister.class)
public class AppConfig {

	@Bean
	public SqlSessionFactory sqlSessionFactory() throws Exception {
		SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
		factoryBean.setDataSource(dataSource());
		return factoryBean.getObject();
	}

	@Bean
	public DataSource dataSource() {
		DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
		driverManagerDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
		driverManagerDataSource.setUsername("root");
		driverManagerDataSource.setPassword("admin");
		driverManagerDataSource.setUrl("jdbc:mysql:///banksys?serverTimezone=UTC&characterEncoding=utf-8");
		return driverManagerDataSource;
	}

	//通过注入的方式 ，手动注入  每次只能注入一个  他同样有生命周期
	@Bean
	public MapperFactoryBean<TDao> userMapper() throws Exception {
		MapperFactoryBean<TDao> factoryBean = new MapperFactoryBean<>(TDao.class);
		factoryBean.setSqlSessionFactory(this.sqlSessionFactory());
		return factoryBean;
	}
}
