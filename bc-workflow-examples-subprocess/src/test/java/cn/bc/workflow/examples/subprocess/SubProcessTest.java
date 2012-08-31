package cn.bc.workflow.examples.subprocess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricVariableUpdate;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * 子流程测试
 * 
 * @author dragon
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional(rollbackFor = { Exception.class })
@ContextConfiguration("classpath:spring-test.xml")
public class SubProcessTest {
	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	@Rule
	public ActivitiRule activitiSpringRule;

	/**
	 * 测试
	 */
	@Test
	@Deployment(resources = { "cn/bc/workflow/examples/subprocess/SubProcess.bpmn20.xml" })
	@Rollback(false)
	public void testDirectProcess() throws Exception {
		String mainKey = "MainProcess";
		String subKey = "SubProcess";

		// 启动主流程
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("mv", "mv");
		ProcessInstance pi = runtimeService.startProcessInstanceByKey(mainKey,
				variables);
		System.out.println("mpi=" + pi.getId());
		Assert.assertNotNull(pi);

		// 验证"汇总"任务
		Task task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test1").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask1", task.getTaskDefinitionKey());
		System.out.println("mt1=" + task.getId());

		// 完成"汇总"任务
		taskService.setVariableLocal(task.getId(), "mv", "v1");
		taskService.complete(task.getId());
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test1").singleResult();
		Assert.assertNull(task);

		// 验证子流程的任务
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test2").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask2", task.getTaskDefinitionKey());
		System.out.println("st1=" + task.getId());

		// 完成子流程的任务
		taskService.setVariableLocal(task.getId(), "sv", "v2");
		taskService.complete(task.getId());
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test2").singleResult();
		Assert.assertNull(task);

		// 验证"归档"任务
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test3").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("usertask3", task.getTaskDefinitionKey());
		System.out.println("mt3=" + task.getId());

		// 完成"归档"任务
		taskService.setVariableLocal(task.getId(), "mv", "v3");
		taskService.complete(task.getId());
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test3").singleResult();
		Assert.assertNull(task);
	}
}