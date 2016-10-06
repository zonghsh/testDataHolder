package org.rci.testdataholder;


import org.rci.testdataholder.generator.SchemaGenerator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testng.annotations.Test;


public class SchemaToolTest {
		
	@Test
	public void testGenOracleSchema(){
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:oracle:thin:@192.168.81.205:1521:platformconnect");
		dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
		dataSource.setUsername("platformconnect");
		dataSource.setPassword("password");
		
		SchemaGenerator schemaGenerator = new SchemaGenerator();
		schemaGenerator.setDbType("Oracle");
		schemaGenerator.setDataSource(dataSource);
		schemaGenerator.setGeneratedFilePath("src/test/resources/schema_oracle.xlsx");
		schemaGenerator.setIncludeTablePrefix("TBL_SHJ_");
		schemaGenerator.init();
		
		schemaGenerator.generate();
	}
	
	@Test
	public void testGenMySqlSchema(){
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:mysql://192.168.81.205:3306/applications");
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUsername("root");
		dataSource.setPassword("root");
		
		SchemaGenerator schemaGenerator = new SchemaGenerator();
		schemaGenerator.setDbType("Mysql");
		schemaGenerator.setDataSource(dataSource);
		schemaGenerator.setGeneratedFilePath("src/test/resources/schema_mysql.xlsx");
		schemaGenerator.setIncludeTablePrefix("TBL_SHJ_");
		schemaGenerator.init();
		
		schemaGenerator.generate();
	}
	
	@Test
	public void testGenMsSqlSchema(){
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:sqlserver://192.168.81.57:1433;databaseName=CEP");
		dataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		dataSource.setUsername("test");
		dataSource.setPassword("test");
		
		SchemaGenerator schemaGenerator = new SchemaGenerator();
		schemaGenerator.setDbType("Mssql");
		schemaGenerator.setDataSource(dataSource);
		schemaGenerator.setGeneratedFilePath("src/test/resources/schema_mssql.xlsx");
		schemaGenerator.setIncludeTablePrefix("TBL_SHJ");
		schemaGenerator.init();
		
		schemaGenerator.generate();
	}
}
