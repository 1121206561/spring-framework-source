package yuhaojun.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yuhaojun.mapper.TDao;

@Service
public class TService {

	@Autowired
	public TDao tDao;

	public void print(){
		System.out.println(tDao.list());
	}
}
