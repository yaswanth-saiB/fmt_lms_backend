package com.fmt.fmt_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.dotenv.ignoreIfMalformed=true",
        "spring.dotenv.ignoreIfMissing=true"
})
class FmtBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
