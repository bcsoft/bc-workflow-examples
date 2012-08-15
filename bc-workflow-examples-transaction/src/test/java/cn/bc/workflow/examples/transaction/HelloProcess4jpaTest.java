package cn.bc.workflow.examples.transaction;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * JPA事务测试
 * <p>
 * 使用Hibernate JPA 默认不会对activiti的异常回滚
 * </p>
 * @author dragon
 * 
 */
public class HelloProcess4jpaTest extends AbstractHelloProcessTest {
	protected ClassPathXmlApplicationContext getApplicationContext() {
		return new ClassPathXmlApplicationContext("spring-test-jpa.xml");
	}
}