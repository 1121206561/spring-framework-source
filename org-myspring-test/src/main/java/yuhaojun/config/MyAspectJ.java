package yuhaojun.config;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
//定义一个切面 我们切点和通知所在的那个类称为切面
@Aspect
public class MyAspectJ {

	//切点  对那些类下的哪些方法进行增强
	//多个连接点的集合叫做切点  一个连接点就是一个方法
	@Pointcut("execution(* yuhaojun.di..*.*(..))")
	public void anyOldTransfer() {
	}

	/**
	 * 通知首先要知道对那些类中的方法进行增强，也就是说需要一个切点
	 * 通知就是切入点的时机 + 切入的类 称之为一个目标
	 */
	@Before("anyOldTransfer()")
	public void advice() {
		System.out.println("----------------------------");
	}
}
