package dartoo.accountService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * 로컬 DB를 MySQL(설치형)에서 PostgreSQL(Docker)로 교체한 이후,
 * @SpringBootTest가 application.yml의 datasource 설정을 그대로 읽어
 * 실제 PostgreSQL 서버에 접속을 시도하는 문제가 발생했다.
 * Docker가 꺼져 있거나 컨테이너가 실행 중이지 않으면 컨텍스트 로딩에 실패해 노란불(Aborted)이 뜬다.
 *
 * MySQL 때는 로컬에 MySQL이 항상 설치되어 있었기 때문에 이 문제가 없었다.
 *
 * @AutoConfigureTestDatabase(replace = Replace.ANY)
 * - application.yml에 설정된 실제 datasource(PostgreSQL)를 무시하고,
 *   테스트 클래스패스에 있는 임베디드 DB(H2)로 자동 교체해준다.
 * - Docker/PostgreSQL 서버가 꺼져 있어도 컨텍스트 로딩이 정상적으로 완료된다.
 * - @DataJpaTest가 별도 설정 없이 H2로 동작하는 것도 이 옵션이 내부적으로 기본 적용되기 때문이다.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
