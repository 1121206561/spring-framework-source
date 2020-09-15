package yuhaojun.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface TDao {

	@Select("SELECT * FROM t_user")
	List<Map<String,Object>> list();
}
