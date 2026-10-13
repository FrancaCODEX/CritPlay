package br.com.critplay.shared;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class DatabaseLock {
    private final JdbcTemplate db;
    public DatabaseLock(JdbcTemplate db){this.db=db;}
    // Deve ser chamado dentro de uma transação. A trava dura até o commit.
    public void acquire(String name){db.queryForObject("SELECT name FROM operation_locks WHERE name=? FOR UPDATE",String.class,name);}
}
