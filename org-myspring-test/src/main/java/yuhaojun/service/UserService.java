package yuhaojun.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	@Autowired
	private OrderService os;

	@Autowired
	private JdbcTemplate jt;

	/**
	 * 事务的传递级别用在方法调用方法的场景中
	 * <p>
	 * 事务的传递级别有7种
	 * 1.REQUIRED  如果当前方法有声明事务，就加入到该事务中，如果没有就新声明一个
	 * 2.SUPPORTS  如果当前方法有声明事务，就加入到该事务中，如果没有就不使用事务
	 * 3.MANDATORY    如果当前方法有声明事务，就加入到该事务中，如果没有就报错
	 * 4.REQUIRES_NEW  不管有没有事务，都新声明一个事务
	 * 5.NOT_SUPPORTED   不管有没有事务，都不使用事务
	 * 6.NEVER   如果有声明的事务报错
	 * 7.NESTED  嵌套事务，
	 */
	@Transactional
	public void consume() {
		jt.update("减余额");
		/**
		 * 为什么一个拥有事务的方法A中调用另一个会挂起事务并创建新事务的方法B，如果使用this调用这个方法B，
		 * 此时方法B抛出了一个一场，此时的方法B的事务会失效的。并不会回滚。
		 *
		 * 原因是：JDK的动态代理。
		 * 在SpringIoC容器中返回的调用的对象是代理对象而不是真实的对象
		 * 只有被动态代理直接调用的才会产生事务。
		 * 这里的this是（TaskService）真实对象而不是代理对象
		 */
		this.payment();
		os.ReduceInventory();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void payment() {
		jt.update("加余额");
	}
}
