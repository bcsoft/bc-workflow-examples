package cn.bc.workflow.examples.transaction;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.springframework.transaction.annotation.Transactional;

public class UserBean {
	private static Log logger = LogFactory.getLog(UserBean.class);
	private RuntimeService runtimeService;
	private TaskService taskService;

	public void setRuntimeService(RuntimeService runtimeService) {
		this.runtimeService = runtimeService;
	}

	public void setTaskService(TaskService taskService) {
		this.taskService = taskService;
	}

	/**
	 * @param appError
	 *            是否抛出自定义的应用异常（非activiti内部的异常）
	 * @param activitiError
	 *            是否抛出activiti内部的异常
	 * @param info
	 *            用于记录流程信息的临时对象
	 * @throws Exception
	 */
	@Transactional
	public String hello(boolean appError, boolean activitiError, Info info)
			throws Exception {
		// here you can do transactional stuff in your domain model
		// and it will be combined in the same transaction as
		// the startProcessInstanceByKey to the Activiti RuntimeService

		String key = "HelloProcess";
		String user1 = "admin";
		Task task;

		// 启动流程（指定编码流程的最新版本，编码对应xml文件中process节点的id值）
		ProcessInstance pi = runtimeService.startProcessInstanceByKey(key);
		info.processInstanceId = pi.getId();
		logger.debug("pi=" + pi.getId());
		Assert.assertNotNull(pi);
		Assert.assertTrue(pi.getProcessDefinitionId().startsWith(key));// ProcessDefinitionId格式为"key:版本号:实例号"

		// 验证任务
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee(user1).singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("任务1", task.getName());
		logger.debug("t1=" + task.getId());

		// 完成任务1(自动创建"审核通过"任务)
		taskService.setVariable(task.getId(), "t1v1", "test");
		if (appError)
			throw new RuntimeException("test");// 抛出异常

		if (activitiError) {
			taskService.complete(UUID.randomUUID().toString());
		} else {
			taskService.complete(task.getId());
		}
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee(user1).singleResult();
		Assert.assertNull(task);

		// 验证流程结束
		assertEquals(0, runtimeService.createProcessInstanceQuery()
				.processInstanceId(pi.getId()).count());

		return pi.getId();
	}
}
