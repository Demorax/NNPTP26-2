package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPulseCliTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private ProjectService projectService;
    private TaskService taskService;
    private TaskPulseCli cli;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outContent, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errContent, true, StandardCharsets.UTF_8);

        InMemoryProjectRepository projectRepo = new InMemoryProjectRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();
        projectService = new ProjectService(projectRepo, taskRepo);
        taskService = new TaskService(taskRepo, projectRepo);

        cli = new TaskPulseCli(projectService, taskService, out, err);
    }

    @Test
    void helpCommandDisplaysAvailableCommands() {
        boolean continueRunning = cli.executeLine("help");
        assertThat(continueRunning).isTrue();
        assertThat(outContent.toString()).contains("Available commands:", "project add", "task add", "worklog log");
    }

    @Test
    void exitCommandReturnsFalse() {
        boolean continueRunning = cli.executeLine("exit");
        assertThat(continueRunning).isFalse();

        continueRunning = cli.executeLine("quit");
        assertThat(continueRunning).isFalse();
    }

    @Test
    void projectLifecycleViaCli() {
        cli.executeLine("project add \"Programming Course\" \"Semester project\"");
        assertThat(outContent.toString()).contains("Project created:", "Programming Course");

        outContent.reset();
        cli.executeLine("project list");
        assertThat(outContent.toString()).contains("Programming Course", "Semester project");
    }

    @Test
    void taskAndWorklogLifecycleViaCli() {
        var project = projectService.createProject("Course", "Desc");
        String projectId = project.getId().toString();

        cli.executeLine("task add " + projectId + " \"Implement parser\" \"RFC 4180\"");
        assertThat(outContent.toString()).contains("Task created:", "Implement parser");

        var task = taskService.listTasks().get(0);
        String taskId = task.getId().toString();

        cli.executeLine("task transition " + taskId + " IN_PROGRESS");
        assertThat(outContent.toString()).contains("transitioned to IN_PROGRESS");

        cli.executeLine("worklog log " + taskId + " 45 --author \"Student\" --desc \"First pass\"");
        assertThat(outContent.toString()).contains("Work logged: 45 min by Student");

        cli.executeLine("task transition " + taskId + " DONE");
        assertThat(outContent.toString()).contains("transitioned to DONE");
    }

    @Test
    void taskListWithoutSortOrdersByTitle() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Beta", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Alpha", "", Priority.HIGH, null, null);

        cli.executeLine("task list");

        assertThat(outContent.toString()).containsSubsequence("Alpha", "Beta");
    }

    @Test
    void taskListSortsByPriority() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.CRITICAL, null, null);
        taskService.createTask(projectId, "Gamma", "", Priority.MEDIUM, null, null);

        cli.executeLine("task list --sort priority");

        assertThat(outContent.toString()).containsSubsequence("Beta", "Gamma", "Alpha");
    }

    @Test
    void taskListSortsByDueDateWithMissingDatesLast() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.MEDIUM, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.MEDIUM, null, LocalDate.of(2026, 12, 1));
        taskService.createTask(projectId, "Gamma", "", Priority.MEDIUM, null, LocalDate.of(2026, 11, 1));

        cli.executeLine("task list --sort duedate");

        assertThat(outContent.toString()).containsSubsequence("Gamma", "Beta", "Alpha");
    }

    @Test
    void taskListSortsByPriorityThenDueDate() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.HIGH, null, LocalDate.of(2026, 12, 1));
        taskService.createTask(projectId, "Beta", "", Priority.LOW, null, LocalDate.of(2026, 10, 1));
        taskService.createTask(projectId, "Gamma", "", Priority.HIGH, null, LocalDate.of(2026, 11, 1));

        cli.executeLine("task list --sort priority-duedate");

        assertThat(outContent.toString()).containsSubsequence("Gamma", "Alpha", "Beta");
    }

    @Test
    void taskListSortValueIsCaseInsensitive() {
        UUID projectId = projectService.createProject("Course", "").getId();
        taskService.createTask(projectId, "Alpha", "", Priority.LOW, null, null);
        taskService.createTask(projectId, "Beta", "", Priority.HIGH, null, null);

        cli.executeLine("task list --sort PRIORITY");

        assertThat(errContent.toString()).isEmpty();
        assertThat(outContent.toString()).containsSubsequence("Beta", "Alpha");
    }

    @Test
    void taskListWithInvalidSortDisplaysAllowedValues() {
        cli.executeLine("task list --sort size");

        assertThat(errContent.toString()).contains("Invalid sort order: 'size'", "title", "priority", "duedate", "priority-duedate");
    }

    @Test
    void unknownCommandDisplaysClearErrorMessage() {
        cli.executeLine("unknownCommand 123");
        assertThat(errContent.toString()).contains("Unknown command: 'unknowncommand'");
    }

    @Test
    void invalidUuidDisplaysClearErrorMessageWithoutStackTrace() {
        cli.executeLine("project show not-a-uuid");
        assertThat(errContent.toString()).contains("Invalid UUID format: 'not-a-uuid'");
        assertThat(errContent.toString()).doesNotContain("Exception in thread", "at cz.upce");
    }

    @Test
    void interactiveSessionCleanExitOnEof() {
        Scanner scanner = new Scanner("help\nexit\n");
        cli.runInteractive(scanner);
        assertThat(outContent.toString()).contains("TaskPulse CLI", "Available commands:");
    }
}
