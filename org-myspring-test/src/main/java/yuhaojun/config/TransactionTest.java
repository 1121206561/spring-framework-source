package yuhaojun.config;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class TransactionTest {
	public static void main(String[] args) {
		TransactionTemplate tx = new TransactionTemplate();
		tx.execute(new TransactionCallback<Object>() {
			@Override
			public Object doInTransaction(TransactionStatus status) {
				/**
				 * 设置回滚点
				 */
				Object savepoint = status.createSavepoint();
				/**
				 * 回滚到某个具体点
				 */
				status.rollbackToSavepoint(savepoint);
				status.flush();
				return null;
			}
		});
	}
}
