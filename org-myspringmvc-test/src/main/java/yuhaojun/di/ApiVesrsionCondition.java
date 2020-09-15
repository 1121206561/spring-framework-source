package yuhaojun.di;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import javax.servlet.http.HttpServletRequest;

public class ApiVesrsionCondition implements RequestCondition<ApiVesrsionCondition> {
	private int apiVersion;
	private String version;

	public ApiVesrsionCondition(int apiVersion, String version) {
		this.apiVersion = apiVersion;
		this.version = version;
	}

	//将不同的筛选条件合并,这里采用的覆盖，即后来的规则生效
	@Override
	public ApiVesrsionCondition combine(ApiVesrsionCondition other) {
		return new ApiVesrsionCondition(other.getApiVersion(), other.getVersion());
	}

	//根据request查找匹配到的筛选条件
	@Override
	public ApiVesrsionCondition getMatchingCondition(HttpServletRequest request) {
		System.out.println(request.getRequestURI());
		String versionStr = request.getParameter("version");
		int version = ApiVesrsionCondition.versionStrToNum(versionStr);
		// 如果请求的版本号大于配置版本号， 则满足，即与请求的
		if (version >= this.apiVersion) {
			return this;
		}
		return null;
	}

	//实现不同条件类的比较，从而实现优先级排序
	@Override
	public int compareTo(ApiVesrsionCondition other, HttpServletRequest request) {

		return other.getApiVersion() - this.apiVersion;
	}

	public int getApiVersion() {
		return apiVersion;
	}

	public String getVersion() {
		return version;
	}

	public static int versionStrToNum(String versionStr) {
		System.out.println(versionStr);
		if (StringUtils.isEmpty(versionStr) || !versionStr.contains(".")) {
			return 0;
		}
		String[] arr = versionStr.split("\\.");
		StringBuilder sb = new StringBuilder();
		for (String num : arr) {
			String str = String.format("%03d", Integer.valueOf(num));
			sb.append(str);
		}
		int version = Integer.valueOf(sb.toString());
		return version;
	}

}
