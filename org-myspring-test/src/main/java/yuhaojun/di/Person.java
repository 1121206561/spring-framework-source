package yuhaojun.di;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.Map;

//@Component
public class Person {

	//属性注入的二种方式
	// 1.Constructor-based dependency injection 构造方法注入
	public Person(Student student) {
		this.student = student;
	}

	// 2.Setter-based dependency injection. 反射注入  反射属性或者方法
	//他并不是自动装配 并且和bytype这个模式没有关系  这个注解只是告送spring需要被注入 , 具体如何去注入就会经过spring的一系列判断
	//所以他并不是一种自动注入
	//反射的属性.set方法
	@Autowired
	Student student;

	public void setStudent(Student student) {
		this.student = student;  //反射的方法直接调用
	}

	/**
	 * 如果有两个类都实现了同一个接口，并且你还需要在某个类中， 拿到这两个bean对象
	 * 你就可以通过   Map<String，接口的类型> 来注入
	 */
	@Autowired
	public Map<String, Student> map;

	/**
	 * 为什么在一个普通的被扫描类也能完成 对象的注入
	 * 每当一个类进行生命周期的过程中都会判断她有没有 @Bean 这个注解 如果有就去doGetBean
	 */
	@Bean
	public void addBean() {

	}
}
