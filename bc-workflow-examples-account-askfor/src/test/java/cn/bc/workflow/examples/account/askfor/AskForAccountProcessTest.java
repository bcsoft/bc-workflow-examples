package cn.bc.workflow.examples.account.askfor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricFormProperty;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帐号申请流程
 * <p>
 * 启动流程-填写帐号信息-人事审批(同意)-开通帐号-结束； 启动流程-填写帐号信息-人事审批(不同意)-结束
 * </p>
 * 
 * @author dragon
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@ContextConfiguration("classpath:spring-test.xml")
public class AskForAccountProcessTest {
	@Autowired
	private RuntimeService runtimeService;
	@Autowired
	private IdentityService identityService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private FormService formService;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	@Rule
	public ActivitiRule activitiSpringRule;

	/**
	 * 测试同意审批的整个正常流程
	 * 
	 * @throws Exception
	 */
	@Test
	@Deployment(resources = {
			"cn/bc/workflow/examples/account/askfor/AskForAccountProcess.bpmn20.xml",
			"cn/bc/workflow/examples/account/askfor/AccountRequestInfo.form" })
	public void testAgreeRequest() throws Exception {
		String formResourceName = "cn/bc/workflow/examples/account/askfor/AccountRequestInfo.form";
		String initiator = "hrj";
		String processKey = "AskForAccountProcess";

		// 设置认证用户
		identityService.setAuthenticatedUserId(initiator);

		// 启动流程（指定编码流程的最新版本，编码对应xml文件中process节点的id值）
		ProcessInstance pi = runtimeService
				.startProcessInstanceByKey(processKey);
		// System.out.println("pi=" + ActivitiUtils.toString(pi));
		Assert.assertNotNull(pi);
		Assert.assertEquals(initiator, runtimeService.getVariable(
				pi.getProcessInstanceId(), "initiator"));
		Assert.assertEquals(initiator, runtimeService.getVariableLocal(
				pi.getProcessInstanceId(), "initiator"));

		// 验证"填写帐号信息"任务
		Task task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee(initiator).singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("填写帐号信息", task.getName());

		// 表单验证
		TaskFormData d = formService.getTaskFormData(task.getId());
		Assert.assertEquals(
				"cn/bc/workflow/examples/account/askfor/AccountRequestInfo.form",
				d.getFormKey());
		List<FormProperty> ps = d.getFormProperties();
		// System.out.println("FormProperties=" + ActivitiUtils.toString(ps));
		Assert.assertNotNull(task);
		Assert.assertEquals(3, ps.size());
		Assert.assertEquals("code", ps.get(0).getId());
		Assert.assertEquals("name", ps.get(1).getId());
		Assert.assertEquals("description", ps.get(2).getId());
		Assert.assertEquals("test", ps.get(2).getValue());// 默认值

		// 验证表单模板：可以获取原始的表单模板的字符串内容，这样就可以自己渲染表单了
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
				.processDefinitionKey(processKey).singleResult();
		Assert.assertNotNull(pd);
		// ==方法一：
		InputStream is = repositoryService.getResourceAsStream(
				pd.getDeploymentId(), formResourceName);// java.io.ByteArrayInputStream
		Assert.assertNotNull(is);
		// System.out.println("form="
		// + new String(FileCopyUtils.copyToByteArray(is)));
		// System.out.println("variables="
		// + runtimeService.getVariables(pi.getProcessInstanceId()));
		// ==方法二：返回的就是表单模板的字符串内容
		Object from = formService.getRenderedTaskForm(task.getId());
		Assert.assertNotNull(from);
		Assert.assertEquals(String.class, from.getClass());
		// System.out.println("from=" + from);

		// 提交表单数据（会自动完成"填写帐号信息"任务，创建"人事审核"任务）
		Map<String, String> properties = new HashMap<String, String>();
		properties.put("code", "code1");
		properties.put("name", "小明");
		String taskId = task.getId();
		formService.submitTaskFormData(task.getId(), properties);
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee(initiator).singleResult();
		Assert.assertNull(task);

		// 这里证明一下历史任务实例的id与流转中任务的id相同而已
		HistoricTaskInstance hti = historyService
				.createHistoricTaskInstanceQuery().taskId(taskId)
				.singleResult();
		Assert.assertNotNull(hti);
		Assert.assertEquals(taskId, hti.getId());

		// 验证提交的数据
		List<HistoricDetail> hfps = historyService.createHistoricDetailQuery()
				.formProperties().taskId(taskId).orderByFormPropertyId().asc()
				.list();
		Assert.assertNotNull(hfps);
		Assert.assertEquals(2, hfps.size());
		HistoricFormProperty hfp = (HistoricFormProperty) hfps.get(0);
		Assert.assertEquals(taskId, hfp.getTaskId());// 这里证明你懂的
		Assert.assertEquals("code", hfp.getPropertyId());
		Assert.assertEquals("code1", hfp.getPropertyValue());
		hfp = (HistoricFormProperty) hfps.get(1);
		Assert.assertEquals("name", hfp.getPropertyId());
		Assert.assertEquals("小明", hfp.getPropertyValue());

		// 验证"人事审核"任务
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("dragon").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("人事审核", task.getName());

		// 审核通过的处理
		taskService.setVariable(task.getId(), "agree", true);
		taskService.complete(task.getId());
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("dragon").singleResult();
		Assert.assertNull(task);

		// 开通账号
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("admin").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("开通账号", task.getName());

		// 流程结束
		taskService.complete(task.getId());
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("admin").singleResult();
		Assert.assertNull(task);
		Assert.assertEquals(0, runtimeService.createProcessInstanceQuery()
				.processInstanceId(pi.getProcessInstanceId()).count());
	}

	/**
	 * 测试同意审批的整个正常流程
	 * 
	 * @throws Exception
	 */
	@Test
	@Deployment(resources = {
			"cn/bc/workflow/examples/account/askfor/AskForAccountProcess.bpmn20.xml",
			"cn/bc/workflow/examples/account/askfor/AccountRequestInfo.form" })
	public void testDisagreeRequest() throws Exception {
		String initiator = "hrj";
		String processKey = "AskForAccountProcess";

		// 设置认证用户
		identityService.setAuthenticatedUserId(initiator);

		// 启动流程
		ProcessInstance pi = runtimeService
				.startProcessInstanceByKey(processKey);
		Assert.assertNotNull(pi);

		// 填写帐号信息
		Task task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee(initiator).singleResult();
		Assert.assertNotNull(task);
		Map<String, String> properties = new HashMap<String, String>();
		properties.put("code", "code1");
		properties.put("name", "小明");
		formService.submitTaskFormData(task.getId(), properties);
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee(initiator).singleResult();
		Assert.assertNull(task);

		// 人事审核
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("dragon").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("人事审核", task.getName());

		// 审核不通过的：没有设置流程变量agree为true，排他网关执行默认的输出流向，流程自动结束
		taskService.setVariable(task.getId(), "agree", false);
		taskService.complete(task.getId());
		task = taskService.createTaskQuery()
				.processInstanceId(pi.getProcessInstanceId())
				.taskAssignee("dragon").singleResult();
		Assert.assertNull(task);

		// 流程结束
		Assert.assertEquals(0, runtimeService.createProcessInstanceQuery()
				.processInstanceId(pi.getProcessInstanceId()).count());
	}
}