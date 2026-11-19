package com.eqmaint.health;

import com.eqmaint.common.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public R<Map<String, Object>> health() {
        Integer ping = jdbcTemplate.queryForObject("select 1", Integer.class);
        String schema = jdbcTemplate.queryForObject("select database()", String.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "eqmaint-backend");
        data.put("dbPing", ping);
        data.put("schema", schema);
        return R.ok(data);
    }
}