package yuhaojun.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component("person.do")
public class PersonController implements HttpRequestHandler {
	//spring MVC 允许通过实现HttpRequestHandler接口把 beanName 作为url，访问url执行重写方法
	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("----");
	}
}
