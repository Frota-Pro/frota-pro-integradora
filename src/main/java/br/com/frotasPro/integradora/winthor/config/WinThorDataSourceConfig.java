package br.com.frotasPro.integradora.winthor.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dois pools de conexão separados pro mesmo Oracle do WinThor:
 *
 * - "principal": o pool já existente, usado pelo job de sincronização em
 *   lote (continua exatamente como antes, definido via spring.datasource.*).
 * - "interativo": pool novo, pequeno e com timeout curto, usado só pelas
 *   consultas sob demanda de nota fiscal/DANFE (disparadas por clique de
 *   usuário). Isolado pra um pico de cliques não disputar conexão com o
 *   job de sincronização, e pra nunca ficar pendurado esperando o Oracle.
 *
 * As credenciais do pool interativo caem por padrão nas mesmas do pool
 * principal (ORACLE_URL/ORACLE_USERNAME/ORACLE_PASSWORD). Se um dia for
 * criado um usuário Oracle só-leitura dedicado, basta definir
 * ORACLE_INTERATIVO_URL/USERNAME/PASSWORD que o sistema passa a usá-lo
 * sem precisar mexer em código.
 */
@Configuration
public class WinThorDataSourceConfig {

    // Antes usava DataSourceBuilder.create().build() + @ConfigurationProperties
    // (prefix = "spring.datasource"), mas esse caminho não traduz a
    // propriedade "url" pro setJdbcUrl() do Hikari nesse cenário — o pool
    // subia sem jdbcUrl definido e falhava com "jdbcUrl is required with
    // driverClassName". Monta manualmente, igual o pool interativo já fazia
    // (que sempre funcionou), pra garantir que o setJdbcUrl é chamado de fato.
    @Primary
    @Bean(name = "winThorDataSource")
    public DataSource winThorDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("winthor-principal");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(name = "jdbcTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("winThorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "winThorInterativoDataSource")
    public DataSource winThorInterativoDataSource(
            @Value("${winthor.interativo.url}") String url,
            @Value("${winthor.interativo.username}") String username,
            @Value("${winthor.interativo.password}") String password
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("winthor-interativo");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.setMaxLifetime(600_000);
        // Não testa conexão na criação do pool: se o WinThor estiver fora do
        // ar bem na hora que a integradora sobe, a aplicação ainda assim
        // inicia normalmente — só essa funcionalidade específica falhará
        // (com a mensagem tratada) até o WinThor voltar.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean(name = "winThorInterativoJdbcTemplate")
    public JdbcTemplate winThorInterativoJdbcTemplate(
            @Qualifier("winThorInterativoDataSource") DataSource dataSource
    ) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // Timeout de execução da query em si (segundos) — evita ficar
        // pendurado se o Oracle do WinThor estiver lento/instável.
        jdbcTemplate.setQueryTimeout(5);
        return jdbcTemplate;
    }
}
