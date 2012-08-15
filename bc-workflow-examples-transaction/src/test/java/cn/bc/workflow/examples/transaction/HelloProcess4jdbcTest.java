package cn.bc.workflow.examples.transaction;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * jdbc事务测试
 * 
 * @author dragon
 * 
 */
public class HelloProcess4jdbcTest extends AbstractHelloProcessTest {
	protected ClassPathXmlApplicationContext getApplicationContext() {
		return new ClassPathXmlApplicationContext("spring-test.xml");
	}
}