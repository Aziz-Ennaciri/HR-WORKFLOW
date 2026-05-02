package ma.rh.ai.hr_workflow;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Application context loads")
class HrWorkflowApplicationTests extends AbstractIntegrationTest {

	@Test
	@DisplayName("Spring context starts with Testcontainers PostgreSQL")
	void contextLoads() {
	}

}
