package org.rci.testdataholder;

import org.rci.testdataholder.loader.DataLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testng.annotations.Test;

public class DataLoaderTest {

	/*@Test
	public void testLoadOracleData(){
		
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:oracle:thin:@192.168.81.205:1521:platformconnect");
		dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
		dataSource.setUsername("platformconnect");
		dataSource.setPassword("password");
		
		DataLoader loader = new DataLoader();
		loader.setDataSource(dataSource);
		loader.setDbType("Oracle");
//		loader.setLoadAllData("true");
		loader.setGeneratedFilePath("src/test/resources/schema_oracle.xlsx");
		loader.init();
		
		loader.loadDataByVersion(1);
	}
	
	@Test
	public void testLoadMysqlData(){
		
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:mysql://192.168.81.205:3306/applications");
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUsername("root");
		dataSource.setPassword("root");
		
		DataLoader loader = new DataLoader();
		loader.setDataSource(dataSource);
		loader.setDbType("Mysql");
		//loader.setLoadAllData("false");
		loader.setGeneratedFilePath("src/test/resources/schema_mysql.xlsx");
		loader.init();
		
		loader.loadDataByVersion(2);
	}
	
	@Test
	public void testLoadMssqlData(){
		
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:sqlserver://192.168.81.57:1433;databaseName=CEP");
		dataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		dataSource.setUsername("test");
		dataSource.setPassword("test");
		
		DataLoader loader = new DataLoader();
		loader.setDataSource(dataSource);
		loader.setDbType("Mssql");
		//loader.setLoadAllData("false");
		loader.setPreloadDataFile("src/test/resources/preloadData.sql");
		loader.setGeneratedFilePath("src/test/resources/schema_mssql.xlsx");
		loader.init();
		
		loader.loadDataByVersion(1);
	}*/
}
