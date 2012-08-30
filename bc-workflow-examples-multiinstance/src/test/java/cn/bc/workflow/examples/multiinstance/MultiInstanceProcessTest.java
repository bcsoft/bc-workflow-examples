package cn.bc.workflow.examples.multiinstance;

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
 * 任务多实例测试流程
 * 
 * @author dragon
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional(rollbackFor = { Exception.class })
@ContextConfiguration("classpath:spring-test.xml")
public class MultiInstanceProcessTest {
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
	@Deployment(resources = { "cn/bc/workflow/examples/multiinstance/MultiInstanceProcess.bpmn20.xml" })
	@Rollback(true)
	public void testDirectProcess() throws Exception {
		String key = "MultiInstanceProcess";

		// 会签的办理人列表
		List<String> assigneeList = new ArrayList<String>();
		assigneeList.add("test1");
		assigneeList.add("test2");

		// 启动流程（指定编码流程的最新版本，编码对应xml文件中process节点的id值）
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("assigneeList", assigneeList);
		ProcessInstance pi = runtimeService.startProcessInstanceByKey(key,
				variables);
		System.out.println("pi=" + pi.getId());
		Assert.assertNotNull(pi);

		// 验证"会签"任务
		List<Task> tasks = taskService.createTaskQuery()
				.processInstanceId(pi.getId()).list();
		Task task1, task2;
		Assert.assertNotNull(tasks);
		Assert.assertEquals(2, tasks.size());
		task1 = tasks.get(0);
		task2 = tasks.get(1);
		Assert.assertEquals("usertask1", task1.getTaskDefinitionKey());
		Assert.assertEquals("usertask1", task2.getTaskDefinitionKey());
		System.out.println("t1=" + task1.getId() + ",t2=" + task2.getId());

		// 完成"会签"任务1
		taskService.setVariable(task1.getId(), "v", "v");
		taskService.setVariableLocal(task1.getId(), "v", "v1");
		taskService.complete(task1.getId());
		Task task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test1").singleResult();
		Assert.assertNull(task);
		tasks = taskService.createTaskQuery().processInstanceId(pi.getId())
				.list();
		Assert.assertNotNull(tasks);
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals("usertask1", tasks.get(0).getTaskDefinitionKey());

		// 完成"会签"任务2
		taskService.setVariableLocal(task2.getId(), "v", "v2");
		taskService.complete(task2.getId());
		task = taskService.createTaskQuery().processInstanceId(pi.getId())
				.taskAssignee("test2").singleResult();
		Assert.assertNull(task);

		// 验证“归档”任务
		tasks = taskService.createTaskQuery().processInstanceId(pi.getId())
				.list();
		Assert.assertNotNull(tasks);
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals("usertask2", tasks.get(0).getTaskDefinitionKey());

		// 验证两个会签任务的历史流程变量
		HistoricVariableUpdate d = (HistoricVariableUpdate) historyService
				.createHistoricDetailQuery().variableUpdates()
				.processInstanceId(pi.getId()).taskId(task1.getId())
				.singleResult();
		Assert.assertNotNull(d);
		Assert.assertEquals("v", d.getVariableName());
		Assert.assertEquals("v1", d.getValue());

		d = (HistoricVariableUpdate) historyService.createHistoricDetailQuery()
				.variableUpdates().processInstanceId(pi.getId())
				.taskId(task2.getId()).singleResult();
		Assert.assertNotNull(d);
		Assert.assertEquals("v", d.getVariableName());
		Assert.assertEquals("v2", d.getValue());

		List<HistoricDetail> pds = historyService.createHistoricDetailQuery()
				.variableUpdates().processInstanceId(pi.getId()).list();
		Assert.assertNotNull(pds);
		System.out.println("size=" + pds.size());
	}
}