package cn.bc.workflow.examples.transaction;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 事务测试
 * 
 * @author dragon
 * 
 */
public abstract class AbstractHelloProcessTest {
	private static Log logger = LogFactory
			.getLog(AbstractHelloProcessTest.class);
	private RepositoryService repositoryService;
	private HistoryService historyService;
	private UserBean userBean;
	private String deploymentId;

	@Before
	public void init() throws Exception {
		// 初始化 spring 配置
		ApplicationContext applicationContext = getApplicationContext();
		repositoryService = applicationContext.getBean(RepositoryService.class);
		historyService = applicationContext.getBean(HistoryService.class);
		userBean = applicationContext.getBean(UserBean.class);

		// 发布流程
		deploymentId = deployProcess();
	}

	@After
	public void close() throws Exception {
		// 删除发布的流程
		if (deploymentId != null) {
			logger.debug("undeploy:" + deploymentId);
			repositoryService.deleteDeployment(deploymentId, true);
			deploymentId = null;
		}
	}

	/**
	 * @return
	 */
	protected abstract ClassPathXmlApplicationContext getApplicationContext();

	/**
	 * 没有异常抛出的测试，事务将提交
	 */
	@Test
	public void testNoException() throws Exception {
		logger.debug("----testNoException----");
		// 执行流程
		Info info = new Info();
		try {
			userBean.hello(true, false, info);
		} catch (Exception e) {
			Assert.assertEquals(RuntimeException.class, e.getClass());
			logger.error("error:class=" + e.getClass() + ",msg="
					+ e.getMessage());
		}
		Assert.assertNotNull(info.processInstanceId);

		// 检测流程历史应该存在
		Assert.assertNotNull(historyService
				.createHistoricProcessInstanceQuery()
				.processInstanceId(info.processInstanceId).singleResult());
	}

	/**
	 * 抛出自定义异常的测试
	 */
	@Test
	public void testAppException() throws Exception {
		logger.debug("----testAppException----");
		// 执行流程
		Info info = new Info();
		try {
			userBean.hello(true, false, info);
		} catch (Exception e) {
			Assert.assertEquals(RuntimeException.class, e.getClass());
			logger.error("error:class=" + e.getClass() + ",msg="
					+ e.getMessage());
		}
		Assert.assertNotNull(info.processInstanceId);

		// 检测流程历史应该不存在
		Assert.assertNull(historyService.createHistoricProcessInstanceQuery()
				.processInstanceId(info.processInstanceId).singleResult());
	}

	/**
	 * 抛出Activiti异常的测试
	 */
	@Test
	public void testActivitiException() throws Exception {
		logger.debug("----testActivitiException----");
		// 执行流程
		Info info = new Info();
		try {
			userBean.hello(false, true, info);
		} catch (Exception e) {
			Assert.assertEquals(ActivitiException.class, e.getClass());
			logger.error("error:class=" + e.getClass() + ",msg="
					+ e.getMessage());
		}
		Assert.assertNotNull(info.processInstanceId);

		// 检测流程历史应该不存在
		Assert.assertNull(historyService.createHistoricProcessInstanceQuery()
				.processInstanceId(info.processInstanceId).singleResult());
	}

	/**
	 * 发布流程
	 * 
	 * @throws Exception
	 */
	private String deployProcess() throws Exception {
		String key = "HelloProcess";
		// ProcessDefinition def = repositoryService
		// .createProcessDefinitionQuery().processDefinitionKey(key)
		// .latestVersion().singleResult();
		// if (def != null)
		// return;

		long c = repositoryService.createDeploymentQuery()
				.deploymentName("HelloProcess.bpmn20.xml").count();

		// xml文件流
		InputStream xmlFile = this.getClass().getResourceAsStream(
				"/cn/bc/workflow/examples/transaction/HelloProcess.bpmn20.xml");
		Assert.assertNotNull(xmlFile);
		DeploymentBuilder db = repositoryService.createDeployment()
				.name("HelloProcess.bpmn20.xml")
				.addInputStream("HelloProcess.bpmn20.xml", xmlFile);

		// 发布
		logger.debug("deploy:" + key);
		org.activiti.engine.repository.Deployment d = db.deploy();
		logger.debug("deploymentId=" + d.getId());

		ProcessDefinition def = repositoryService
				.createProcessDefinitionQuery().processDefinitionKey(key)
				.latestVersion().singleResult();
		logger.debug("definitionId=" + def.getId());

		// 验证
		Assert.assertNotNull(d);
		assertEquals(c + 1, repositoryService.createDeploymentQuery()
				.deploymentName("HelloProcess.bpmn20.xml").count());

		return d.getId();
	}
}