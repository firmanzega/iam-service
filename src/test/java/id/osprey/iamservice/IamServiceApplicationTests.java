package id.osprey.iamservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IamServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
