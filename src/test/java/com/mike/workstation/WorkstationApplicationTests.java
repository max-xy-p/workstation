package com.mike.workstation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "DB_URL=jdbc:mysql://localhost:3306/workstation",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "APP_ADMIN_USERNAME=owner",
        "APP_ADMIN_PASSWORD_HASH=$2a$10$LKcG8Fmj65ZSAK2augYy/.n66.VjHTNeHpKUH8DeWSbBJhljfiU7O",
        "DASHBOARD_RECENT_LIMIT=5",
        "NOTE_IMPORT_ALLOWED_ROOTS=D:/notes",
        "NOTE_IMPORT_MAX_FILE_BYTES=1048576"
})
class WorkstationApplicationTests {

    @Test
    void contextLoads() {
    }

}
