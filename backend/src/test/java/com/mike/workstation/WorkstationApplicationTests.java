package com.mike.workstation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "DB_URL=jdbc:mysql://localhost:3306/workstation",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "DASHBOARD_RECENT_LIMIT=5",
        "NOTE_IMPORT_ALLOWED_ROOTS=D:/notes",
        "NOTE_IMPORT_MAX_FILE_BYTES=1048576"
})
class WorkstationApplicationTests {

    @Test
    void contextLoads() {
    }

}
