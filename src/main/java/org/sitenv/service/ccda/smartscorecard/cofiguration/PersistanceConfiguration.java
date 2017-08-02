package org.sitenv.service.ccda.smartscorecard.cofiguration;

import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.sitenv.service.ccda.smartscorecard.loader.VocabularyLoadRunner;
import org.sitenv.service.ccda.smartscorecard.loader.VocabularyLoaderFactory;
import org.sitenv.service.ccda.smartscorecard.model.ScorecardProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.hibernate4.HibernateExceptionTranslator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@Configuration
@EnableTransactionManagement
@PropertySource({"classpath:config.properties"})
@ComponentScan("org.sitenv.service.ccda.smartscorecard")
@EnableJpaRepositories(
		 entityManagerFactoryRef = "inmemoryEntityManagerFactory", 
	     transactionManagerRef = "inmemoryTransactionManager",
	     basePackages = {"org.sitenv.service.ccda.smartscorecard.repositories.inmemory"})
public class PersistanceConfiguration {
    private static final String HSQL_JDBC_URL_TEMPLATE = "jdbc:hsqldb:file:scorecarddatabase/db;hsqldb.default_table_type=cached;hsqldb.write_delay_millis=10;readonly=false";
    @Value("classpath:schema.sql")
    private Resource HSQL_SCHEMA_SCRIPT;
    
    @Bean(name="inmemoryEntityManagerFactory")
    @Primary
    public EntityManagerFactory entityManagerFactory() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(true);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("org.sitenv.service.ccda.smartscorecard.entities.inmemory");
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", "none");
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
        jpaProperties.put("hibernate.format_sql", "true");
        jpaProperties.put("hibernate.show_sql", "false");
        factory.setDataSource(dataSource());
        factory.setJpaProperties(jpaProperties);
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean(name="inmemoryTransactionManager")
    @Primary
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory());
        return txManager;
    }

    @Bean
    public HibernateExceptionTranslator hibernateExceptionTranslator() {
        return new HibernateExceptionTranslator();
    }

    @Autowired
    @Bean
    public DataSourceInitializer dataSourceInitializer(final DataSource dataSource) {
        final DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator());
        return initializer;
    }

    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(HSQL_SCHEMA_SCRIPT);
        return populator;
    }

    @Bean(name = "inmemoryDataSource")
    @Primary
    public DataSource dataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setUrl(HSQL_JDBC_URL_TEMPLATE);
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setInitialSize(3);
        ds.setDriverClassName("org.hsqldb.jdbcDriver");
        return ds;
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocalOverride(true);
        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    public ServiceLocatorFactoryBean vocabularyLoaderFactoryServiceLocatorFactoryBean() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(VocabularyLoaderFactory.class);
        return bean;
    }

    @Bean
    public VocabularyLoaderFactory vocabularyLoaderFactory() {
        return (VocabularyLoaderFactory) vocabularyLoaderFactoryServiceLocatorFactoryBean().getObject();
    }
    
	@Autowired
    @Bean(name="scorecardProperties")
    @SuppressWarnings("unused")
    public ScorecardProperties scorecardPropertiesLoader(final Environment environment){
    	ScorecardProperties scorecardProperties = new ScorecardProperties();
    	scorecardProperties.setIgConformanceCall(
    			ApplicationConfiguration.OVERRIDE_SCORECARD_XML_CONFIG && ApplicationConfiguration.IG_CONFORMANCE_CALL ? 
    					ApplicationConfiguration.IG_CONFORMANCE_CALL :
    			Boolean.parseBoolean(environment.getProperty("scorecard.igConformanceCall")));
    	scorecardProperties.setCertificationResultsCall(
    			ApplicationConfiguration.OVERRIDE_SCORECARD_XML_CONFIG && ApplicationConfiguration.CERTIFICATION_RESULTS_CALL ? 
    					ApplicationConfiguration.CERTIFICATION_RESULTS_CALL :
    			Boolean.parseBoolean(environment.getProperty("scorecard.certificatinResultsCall")));
    	scorecardProperties.setIgConformanceURL(
    			ApplicationConfiguration.OVERRIDE_SCORECARD_XML_CONFIG ? ApplicationConfiguration.REFERENCE_VALIDATOR_URL :
    			environment.getProperty("scorecard.igConformanceUrl"));
    	scorecardProperties.setCertificatinResultsURL(
    			ApplicationConfiguration.OVERRIDE_SCORECARD_XML_CONFIG ? ApplicationConfiguration.REFERENCE_VALIDATOR_URL :    			
    			environment.getProperty("scorecard.certificationResultsUrl"));
        return scorecardProperties;
    }
    
    @Autowired
    @Bean
    VocabularyLoadRunner vocabularyLoadRunner(final Environment environment, final VocabularyLoaderFactory vocabularyLoaderFactory, final  DataSourceInitializer dataSourceInitializer, final DataSource dataSource){
        VocabularyLoadRunner vocabularyLoadRunner = null;
        String localCodeRepositoryDir = environment.getProperty("vocabulary.localCodeRepositoryDir");
        vocabularyLoadRunner = new VocabularyLoadRunner();
        System.out.println("LOADING VOCABULARY DATABASES FROM THE FOLLOWING RESOURCES:CODES - " + localCodeRepositoryDir);
        vocabularyLoadRunner.setCodeDirectory(localCodeRepositoryDir);
        vocabularyLoadRunner.setDataSource(dataSource);
        vocabularyLoadRunner.setVocabularyLoaderFactory(vocabularyLoaderFactory);
        return vocabularyLoadRunner;
    }
}
