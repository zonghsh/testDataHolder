package org.rci.testdataholder.generator;

import java.io.File;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.rci.testdataholder.db.DbConnect;
import org.rci.testdataholder.db.MssqlDbConnect;
import org.rci.testdataholder.db.MysqlDbConnect;
import org.rci.testdataholder.db.OracleDbConnect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class SchemaGenerator {
	
	private Logger log = Logger.getLogger(getClass());
	
	@Autowired
	@Qualifier("genSchemaDataSource")
	private DataSource dataSource;
	
	private String generatedFilePath;
	
	private String dbType;
	
	private String includeTablePrefix;
	
	private DbConnect dbConnect;
	
	private Generator generator;	
	
	private boolean isUpdate = false;
	
	private String auditTblPrefix;
	
	private String auditTblSuffix;
	
	private String excludeTables;

	public void generate(){
		
		String path = null;
		if(isUpdate){
			log.info("Update schema.");
			int ind = generatedFilePath.indexOf(".");
			path = generatedFilePath.substring(0,ind)+"_tmp"+generatedFilePath.substring(ind);
		}else{
			path = generatedFilePath;
		}
		
		log.info("The generated file path: " + generatedFilePath);
		
		try{
			generator.generateSchema(path);
			
			if(isUpdate){
				File file = new File(generatedFilePath);
				file.delete();
				
				File fileGen = new File(path);
				fileGen.renameTo(file);
			}
			log.info("Schema generated successful.");
			
		}catch(Exception e){
			log.error("Schema generated failed.", e);
			throw new RuntimeException(e);
		}
	}
	
	public void init(){
		if(generatedFilePath == null || "".equals(generatedFilePath.trim())){
			log.error("Please define the path of generated schema file.");
			return;
		}
				
		File file = new File(generatedFilePath);
		isUpdate = file.exists();
		
		if("Oracle".equalsIgnoreCase(dbType)){
			dbConnect = new OracleDbConnect(dataSource, includeTablePrefix);
		}else if("Mysql".equalsIgnoreCase(dbType)){
			dbConnect = new MysqlDbConnect(dataSource, includeTablePrefix);
		}else if("Mssql".equalsIgnoreCase(dbType)){
			dbConnect = new MssqlDbConnect(dataSource, includeTablePrefix);
		}
		
		dbConnect.setAuditTblPrefix(auditTblPrefix);
		dbConnect.setAuditTblSuffix(auditTblSuffix);
		dbConnect.setExcludeTables(excludeTables);
		
		if(generatedFilePath.toLowerCase().endsWith("xlsx")){
			generator = new XlsxGenerator(generatedFilePath, dbConnect, isUpdate);
		}
	}
	
	public void setGeneratedFilePath(String generatedFilePath) {
		this.generatedFilePath = generatedFilePath;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setDbType(String dbType) {
		this.dbType = dbType;
	}

	public void setIncludeTablePrefix(String includeTablePrefix) {
		this.includeTablePrefix = includeTablePrefix;
	}

	public void setAuditTblPrefix(String auditTblPrefix) {
		this.auditTblPrefix = auditTblPrefix;
	}

	public void setAuditTblSuffix(String auditTblSuffix) {
		this.auditTblSuffix = auditTblSuffix;
	}

	public String getExcludeTables() {
		return excludeTables;
	}

	public void setExcludeTables(String excludeTables) {
		this.excludeTables = excludeTables;
	}
}
