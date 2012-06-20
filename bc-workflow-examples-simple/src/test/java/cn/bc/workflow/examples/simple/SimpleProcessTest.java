package cn.bc.workflow.examples.simple;

import static org.junit.Assert.assertEquals;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.DelegationState;
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
 * 简易测试流程的测试：开始-提出申请(发给用户dragon)-审核通过(发给用户组chaojiguanligang-admin用户在这个组里面)- 结束
 * 
 * @author dragon
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@ContextConfiguration("classpath:spring-test.xml")
public class SimpleProcessTest {
	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	@Rule
	public ActivitiRule activitiSpringRule;

	/**
	 * 直接流转的测试
	 */
	@Test
	@Deployment(resources = { "cn/bc/workflow/examples/simple/SimpleProcess.bpmn20.xml" })
	public void testDirectProcess() throws Exception {
		// 启动流程（指定编码流程的最新版本，编码对应xml文件中process节点的id值）
		ProcessInstance pi = runtimeService
				.startProcessInstanceByKey("SimpleProcess");
		// System.out.println("pi=" + ActivitiUtils.toString(pi));
		Assert.assertNotNull(pi);
		Assert.assertTrue(pi.getProcessDefinitionId().startsWith(
				"SimpleProcess:"));// ProcessDefinitionId格式为"key:版本号:实例号"
		Assert.assertNull(pi.getBusinessKey());

		// 验证"提出申请"任务
		Task task = taskService.createTaskQuery().taskAssignee("dragon")
				.singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask1", task.getTaskDefinitionKey());
		Assert.assertEquals("提出申请", task.getName());
		Assert.assertEquals("dragon", task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());
		Assert.assertEquals(pi.getProcessDefinitionId(),
				task.getProcessDefinitionId());
		Assert.assertEquals(pi.getProcessInstanceId(),
				task.getProcessInstanceId());
		Assert.assertEquals(pi.getProcessInstanceId(), task.getExecutionId());
		Assert.assertNull(task.getDescription());

		// 完成"提出申请"任务(自动创建"审核通过"任务)
		taskService.complete(task.getId());
		task = taskService.createTaskQuery().taskAssignee("dragon")
				.singleResult();
		Assert.assertNull(task);

		// 验证管理岗有一条任务
		task = taskService.createTaskQuery()
				.taskCandidateGroup("chaojiguanligang").singleResult();
		// System.out.println("task=" + ActivitiUtils.toString(task));
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask2", task.getTaskDefinitionKey());
		Assert.assertEquals("审核通过", task.getName());
		Assert.assertNull(task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());

		// 管理员领取任务
		taskService.claim(task.getId(), "admin");
		task = taskService.createTaskQuery()
				.taskCandidateGroup("chaojiguanligang").singleResult();
		Assert.assertNull(task);
		task = taskService.createTaskQuery().taskAssignee("admin")
				.singleResult();
		// System.out.println("task=" + ActivitiUtils.toString(task));
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask2", task.getTaskDefinitionKey());
		Assert.assertEquals("审核通过", task.getName());
		Assert.assertEquals("admin", task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());

		// 管理员完成任务(自动结束流程)
		taskService.complete(task.getId());
		assertEquals(0, runtimeService.createProcessInstanceQuery().count());
	}

	/**
	 * 委派任务的测试
	 */
	@Test
	@Deployment(resources = { "cn/bc/workflow/examples/simple/SimpleProcess.bpmn20.xml" })
	public void testDelegateProcess() throws Exception {
		Task task;

		// 启动流程（指定编码流程的最新版本，编码对应xml文件中process节点的id值）
		ProcessInstance pi = runtimeService
				.startProcessInstanceByKey("SimpleProcess");
		// System.out.println("pi=" + ActivitiUtils.toString(pi));
		Assert.assertNotNull(pi);

		// 验证"提出申请"任务
		task = taskService.createTaskQuery().taskAssignee("dragon")
				.singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("提出申请", task.getName());
		Assert.assertEquals("dragon", task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());

		// 委派任务给admin
		taskService.delegateTask(task.getId(), "admin");
		task = taskService.createTaskQuery().taskAssignee("dragon")
				.singleResult();
		Assert.assertNull(task);
		
		task = taskService.createTaskQuery().taskAssignee("admin")
				.singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals(DelegationState.PENDING, task.getDelegationState());
		Assert.assertNull("dragon", task.getOwner());// 拥有者
		Assert.assertEquals("admin", task.getAssignee());// 处理人

		// admin完成被委派的任务(自动创建"审核通过"任务)
		taskService.complete(task.getId());
		task = taskService.createTaskQuery().taskAssignee("admin")
				.singleResult();
		Assert.assertNull(task);
		task = taskService.createTaskQuery().taskAssignee("dragon")
				.singleResult();
		Assert.assertNull(task);

		// 验证管理岗有一条任务
		task = taskService.createTaskQuery()
				.taskCandidateGroup("chaojiguanligang").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("审核通过", task.getName());
		Assert.assertNull(task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());

		// 管理员领取任务
		taskService.claim(task.getId(), "admin");
		task = taskService.createTaskQuery()
				.taskCandidateGroup("chaojiguanligang").singleResult();
		Assert.assertNull(task);
		task = taskService.createTaskQuery().taskAssignee("admin")
				.singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("审核通过", task.getName());
		Assert.assertEquals("admin", task.getAssignee());// 处理人
		Assert.assertNull(task.getOwner());

		// 管理员完成任务(自动结束流程)
		taskService.complete(task.getId());
		assertEquals(0, runtimeService.createProcessInstanceQuery().count());
	}
}