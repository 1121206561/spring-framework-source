package yuhaojun.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	@Autowired
	private JdbcTemplate jt;

	//默认的事务传递级别
	@Transactional(propagation = Propagation.REQUIRED)
	public void ReduceInventory() {
		jt.update("减库存");
	}
}
