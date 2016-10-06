package org.rci.testdataholder.loader;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.rci.testdataholder.db.DbConnect;
import org.rci.testdataholder.db.MssqlDbConnect;
import org.rci.testdataholder.db.MysqlDbConnect;
import org.rci.testdataholder.db.OracleDbConnect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class DataLoader {
	
	private Logger log = Logger.getLogger(this.getClass());
	
	@Autowired
	@Qualifier("dataSource")
	private DataSource dataSource;
	
	private String generatedFilePath;
	
	private String dbType;
	
	private String loadAllData;
	
	private String preloadDataFile;
	
	private String specialProcessFile;
	
	private Loader loader;
	private DbConnect dbConnect;
	
	private String auditTblPrefix;
	
	private String auditTblSuffix;
	
	/**
	 * Different test case should use different data version. And there should be no dependency between different
	 * data version. E.g. The foreign key in data version 2 shouldn't reference to the data version 1.
	 *                 
	 * @param version The first version is the main data version. 
	 */
	public void loadDataByVersion(int version){
		try{
			loader.loadDataByVersion(version);
		}catch(Exception e){
			log.error(e.getMessage(),e);
		}
	}
	
	public void initDatabaseData(){
		try{
			loader.initDatabaseData();
		}catch(Exception e){
			log.error(e.getMessage(),e);
		}
		
	}
		
	public void init(){
		if("Oracle".equals(dbType)){
			dbConnect = new OracleDbConnect(dataSource);
		}else if("Mysql".equals(dbType)){
			dbConnect = new MysqlDbConnect(dataSource);
		}else if("Mssql".equals(dbType)){
			dbConnect = new MssqlDbConnect(dataSource);
		}
		
		//remove loadAllData by now. It should always to be true. Because we need to delete all data in excel before each test case. In this way,
		//the test case will always use the same db data especially for search test case.
		boolean loadAll = Boolean.parseBoolean(loadAllData == null ? "true" : "false");
		if(generatedFilePath.toLowerCase().endsWith("xlsx")){ 
			loader = new XlsxDataLoader(dbConnect, generatedFilePath, loadAll);
			
		}else if(generatedFilePath.toLowerCase().endsWith("csv")){
			//TODO
		}	
		loader.setPreloadDataFile(preloadDataFile);
		loader.setSpecialProcessFile(specialProcessFile);
		loader.setAuditTblPrefix(auditTblPrefix);
		loader.setAuditTblSuffix(auditTblSuffix);
	}
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setGeneratedFilePath(String generatedFilePath) {
		this.generatedFilePath = generatedFilePath;
	}

	public void setDbType(String dbType) {
		this.dbType = dbType;
	}

	public String getPreloadDataFile() {
		return preloadDataFile;
	}

	public void setPreloadDataFile(String preloadDataFile) {
		this.preloadDataFile = preloadDataFile;
	}

	public String getSpecialProcessFile() {
		return specialProcessFile;
	}

	public void setSpecialProcessFile(String specialProcessFile) {
		this.specialProcessFile = specialProcessFile;
	}

	public void setAuditTblPrefix(String auditTblPrefix) {
		this.auditTblPrefix = auditTblPrefix;
	}

	public void setAuditTblSuffix(String auditTblSuffix) {
		this.auditTblSuffix = auditTblSuffix;
	}

	/*public void setLoadAllData(String loadAllData) {
		this.loadAllData = loadAllData;
	}*/
}
