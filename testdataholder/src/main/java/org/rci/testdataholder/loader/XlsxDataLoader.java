package org.rci.testdataholder.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rci.testdataholder.Constants;
import org.rci.testdataholder.db.DbConnect;
import org.rci.testdataholder.util.StringUtil;
import org.springframework.util.StringUtils;


public class XlsxDataLoader implements Loader{

	private Logger log = Logger.getLogger(this.getClass());
	
	//key - data version; value -- Map(key -- table name; value -- list of sqls)
	private Map<String, Map<String,List<String>>> insertMap ;
//	private Map<String, Map<String,List<String>>> deleteMap;
	private Map<String, Map<String,List<String>>> updateAfterInsertMap;
	private Map<String, Map<String,List<String>>> updateBeforeDelMap;
	
	private Map<String, List<String>> allDelMap; //key - table name, value - list of all delete sql in the table
	
	private FormulaEvaluator evaluator;	
		
	private DbConnect dbConnect;
	
	private String generatedFilePath;
	
	private boolean loadAllData = false;
	
	private Map<String, String> versionTableMap; //key -- data version; value -- table names
	
	private Map<String, Integer> colNumMap; //key -- table name; value -- the column index of delete sql
	
	private Map<String, String> parentChildsMap; //key - parent table name; value -- child tables
	
	private String[] sortedTableArr; //the parent table is before child table.
	
	private String preloadDataFile;
	
	private String specialProcessFile;
	
	private String auditTblPrefix;
	
	private String auditTblSuffix;
	
	private List<String> auditTableDelSqls;
	
	//key - table name, value - update fk sql
	private Map<String, String> updateBeforeDeleteMap = new HashMap<String, String>();
	
	public void setPreloadDataFile(String filePath){
		preloadDataFile = filePath;
	}
	
	public  XlsxDataLoader(DbConnect dbConnect, String generatedFilePath, boolean loadAllData ){
		this.dbConnect = dbConnect;
		this.generatedFilePath = generatedFilePath;
		this.loadAllData = loadAllData;
	}
		
	public void initDatabaseData() throws Exception{
		if(sortedTableArr == null){
			auditTableDelSqls = new ArrayList<String>();
			InputStream ins = null;
			try{
				
				ins = new FileInputStream(generatedFilePath);
				XSSFWorkbook book = new XSSFWorkbook(ins);
				
				XSSFSheet indSheet = book.getSheet(Constants.INDEX_SHEET_NAME);
				String sortedTables = indSheet.getRow(0).getCell(Constants.SORTED_TABLE_COL_IND + 1).getStringCellValue();// parent table is in the front of child table
				sortedTableArr = sortedTables.split(Constants.SEPARATE);
				
			}catch(Exception e){
				versionTableMap = null;
				log.error("Error when reading excel.");
				throw e;
				
			}finally{
				if(ins != null){
					ins.close();
				}
			}
		}
		cleanAndInitDatabaseData();
	}
	
	public void loadDataByVersion(int... versions) throws Exception{
		if(versionTableMap == null){
			init();
		}
		
		if(!loadAllData){
			initSqlMap(versions);
		}
				
		List<String> updateBeforeDelSqls = new ArrayList<String>();
		List<String> delSqls = new ArrayList<String>();
		List<String> insertSqls = new ArrayList<String>();
		List<String> updateAfterInsertSqls = new ArrayList<String>();
		String[] tables = null;
		
		for(int i = versions.length - 1; i >= 0 ; i--){
			String v = versions[i] + "";
			if(versionTableMap.get(v) == null){
				log.warn("The table names for version " + v + " doesn't been specified in \"" + Constants.VERION_TABLE_MAP_SHEET_NAME + "\" sheet.");
			}
		}
						
		for(String tbl : sortedTableArr){
			addAllDeleteSql(delSqls, tbl);
			for(int i = versions.length - 1; i >= 0 ; i--){
				String v = versions[i] + "";
				if(versionTableMap.get(v) != null){
					tables = versionTableMap.get(v).split(Constants.SEPARATE);
					if(isExist(tbl, tables)){
						addSqlToList(updateBeforeDelSqls, updateBeforeDelMap.get(v), tbl);
//						addSqlToList(delSqls, deleteMap.get(v), tbl);
						addSqlToList(insertSqls, insertMap.get(v), tbl);
						addSqlToList(updateAfterInsertSqls, updateAfterInsertMap.get(v), tbl);
					}
				}
			}			
		}
		
		if(StringUtil.isNotEmpty(preloadDataFile) || StringUtil.isNotEmpty(specialProcessFile)){
			cleanAndInitDatabaseData();
			
		}else{			
			executeSqls(updateBeforeDelSqls, false);		
			executeSqls(delSqls, true);
		}
		
		executeSqls(insertSqls, false);
		executeSqls(updateAfterInsertSqls, false);
	}
		
	private boolean isAuditTable(String tbl){
		if(StringUtil.isEmpty(auditTblPrefix) && StringUtil.isEmpty(auditTblSuffix)){
			return false;
		}
		
		boolean flag = true;
		if(StringUtil.isNotEmpty(auditTblPrefix)){
			flag = tbl.startsWith(auditTblPrefix);
		}
		if(StringUtil.isNotEmpty(auditTblSuffix)){
			flag = flag && tbl.endsWith(auditTblSuffix);
		}
		return flag;
	}
	
	private void cleanAndInitDatabaseData(){
		List<String> specialProcessSqls = getSpecialProcessSql();
		List<String> delSqls = new ArrayList<String>();
		for(String tbl : sortedTableArr){
			String sql = findDelSql(tbl, specialProcessSqls);
			if(sql == null){
				if(isAuditTable(tbl)){
					auditTableDelSqls.add("DELETE FROM " + tbl);
				}else{
					delSqls.add("DELETE FROM " + tbl);
				}
				
			}else{
				delSqls.add(sql);
				specialProcessSqls.remove(sql);
			}
		}
				
		executeSqls(delSqls, true);
		
		List<String> insertSqls = new ArrayList<String>();
		
		try{
			InputStream ins = new FileInputStream(new File(preloadDataFile));
			String[] scripts = StringUtils.delimitedListToStringArray(StringUtil.stripComments(IOUtils.readLines(ins)), ";");
			for (int j = 0; j < scripts.length; j++) {
				String script = scripts[j].trim();
				if (StringUtils.hasText(script)) {
					script = StringUtils.capitalize(script).trim();
					insertSqls.add(script);
				}
			}
			
		}catch(FileNotFoundException e){
			log.error("Can't find preload data file: " + preloadDataFile);
			
		}catch(IOException e){
			log.error("Cannot load script from [" + preloadDataFile + "]", e);
		}
				
		if(insertSqls.size() > 0){
			executeSqls(insertSqls, false);
		}
		
		if(auditTableDelSqls.size() > 0){
			executeSqls(auditTableDelSqls, false);
		}
		
		//execute sql		
		for(String sql : specialProcessSqls){
			executeSql(sql);
		}
	}
	
	private List<String> getSpecialProcessSql(){
		if(StringUtil.isEmpty(specialProcessFile)){
			return new ArrayList<String>();
		}
		
		List<String> specialProcessSqls = new ArrayList<String>();
		try{
			InputStream ins = new FileInputStream(new File(specialProcessFile));
			String[] scripts = StringUtils.delimitedListToStringArray(StringUtil.stripComments(IOUtils.readLines(ins)), ";");
			for (int j = 0; j < scripts.length; j++) {
				String script = scripts[j].trim();
				if (StringUtils.hasText(script)) {
					script = StringUtils.capitalize(script).trim();
					specialProcessSqls.add(script);
				}
			}
			
		}catch(FileNotFoundException e){
			log.error("Can't find preload data file: " + preloadDataFile);
			
		}catch(IOException e){
			log.error("Cannot load script from [" + preloadDataFile + "]", e);
		}
		return specialProcessSqls;
	}
	
	private String findDelSql(String tbl, List<String> specialProcessSqls){
		for(String sql : specialProcessSqls){
			if(sql.startsWith("DELETE FROM " + tbl + " ")){
				return sql;
			}
		}
		return null;
	}
	
	private void addAllDeleteSql(List<String> delSqls, String tbl){
		List<String> list = allDelMap.get(tbl);
		if(list != null && !list.isEmpty()){
			delSqls.addAll(allDelMap.get(tbl));
		}
		
	}
	
	private void initSqlMap(int... versions) throws Exception{
		InputStream ins = null;
		try{
			ins = new FileInputStream(generatedFilePath);
			XSSFWorkbook book = new XSSFWorkbook(ins);
			List<String> list = new ArrayList<String>();
			for(int v : versions){
				list.add(v + "");
			}
			initSqlMap(book, list);
			
		}catch(Exception e){
			log.error("Error when reading excel.");
			throw e;
		}finally{
			if(ins != null){
				ins.close();
			}
		}
	}
	
	private void addSqlToList(List<String> sqls, Map<String,List<String>> tblSqlMap, String tblName){
		if(tblSqlMap != null && tblSqlMap.containsKey(tblName)){
			sqls.addAll(tblSqlMap.get(tblName));
		}
	}
	
	private void init() throws Exception{
		versionTableMap = new HashMap<String, String>();
		auditTableDelSqls = new ArrayList<String>();
		
		InputStream ins = null;
		try{
			
			ins = new FileInputStream(generatedFilePath);
			XSSFWorkbook book = new XSSFWorkbook(ins);
			
			initVersionTableMap(book);
			initColMap(book);
			
			if(loadAllData){
				initSqlMap(book, null);
			}
			
		}catch(Exception e){
			versionTableMap = null;
			log.error("Error when reading excel.");
			throw e;
			
		}finally{
			if(ins != null){
				ins.close();
			}
		}
	}
	
	private void initColMap(XSSFWorkbook book){
		log.debug("get column number of each table");
		
		colNumMap = new HashMap<String,Integer>();
		parentChildsMap = new HashMap<String, String>();
		
		XSSFSheet indSheet = book.getSheet(Constants.INDEX_SHEET_NAME);
		XSSFRow row = null;
		XSSFCell cell = null;
		String tblNm = null;
		
		for(int i = 1 ; i <= indSheet.getLastRowNum(); i++){ //the first row is header, so begin 1
			row = indSheet.getRow(i);
			if(row.getCell(0) != null && row.getCell(1) != null){
				tblNm = row.getCell(0).getStringCellValue();
				colNumMap.put(tblNm, (int)row.getCell(1).getNumericCellValue());
				
				cell = row.getCell(Constants.UPDATE_SQL_COL_IND + 1);
				String childs = cell.getStringCellValue();
				if(!"".equals(childs.trim())){
					parentChildsMap.put(tblNm,  childs);
				}
				
			}
		}
	}
	
	private void initSqlMap(XSSFWorkbook book, List<String> versions){
		log.debug("Get all delete, update and insert sql.");
		insertMap = new HashMap<String, Map<String,List<String>>>();
//		deleteMap = new HashMap<String, Map<String,List<String>>>();
		allDelMap = new HashMap<String, List<String>>();
		updateAfterInsertMap = new HashMap<String, Map<String,List<String>>>();
		updateBeforeDelMap = new HashMap<String, Map<String,List<String>>>();
		
		evaluator = book.getCreationHelper().createFormulaEvaluator();
		
		XSSFSheet indSheet = book.getSheet(Constants.INDEX_SHEET_NAME);
		String sortedTables = indSheet.getRow(0).getCell(Constants.SORTED_TABLE_COL_IND + 1).getStringCellValue();// parent table is in the front of child table
		sortedTableArr = sortedTables.split(Constants.SEPARATE);
		
		XSSFRow row = null;
		XSSFCell cell = null;
		for(int i = 1 ; i <= indSheet.getLastRowNum(); i++){
			row = indSheet.getRow(i);
			if(row == null || row.getCell(0) == null){
				continue;
			}
			
			String tblNm = row.getCell(0).getStringCellValue();
			if(StringUtil.isEmpty(tblNm)){
				continue;
			}
			
			cell = row.getCell(Constants.UPDATE_SQL_COL_IND);
			if(cell == null){
				continue;
			}
			updateBeforeDeleteMap.put(tblNm, cell.getStringCellValue());
		}
								
		XSSFSheet sheet = null;
		
		for(String tableNm : sortedTableArr){
			sheet = book.getSheet(tableNm);
			if(sheet == null){ // the schema maybe didn't synchronized with db
				continue;
			}
			getSqlsForLoadData(sheet, versions);
		}
	}
	
	/**
	 * 
	 * @param sheet
	 * @param versions if null, load all the data in this sheet
	 */
	private void getSqlsForLoadData(XSSFSheet sheet, List<String> versions){
		XSSFRow row = null;
		XSSFCell cell = null;		
		
		String tableName = sheet.getSheetName();
		int delSqlColIndex = colNumMap.get(tableName);
		
		int rowNum = Constants.FIRST_CONTENT_ROW_IND;  // the Row 1 ~ 3 are header
		for(; rowNum <= sheet.getLastRowNum(); rowNum++){
			row = sheet.getRow(rowNum);
			if(row == null){// the row is empty
				continue;
			}
			
			cell = row.getCell(0);
			if(cell == null){ //there is no version in this row, it's dirty data.
				continue;
			}
			
			String version = cell.getStringCellValue();			
			boolean loadCurrentDataLine = false;
			
			if(version != null && !"".equals(version.trim())){
				if(versions == null){
					loadCurrentDataLine = true;
				}else{
					if(versions.contains(version)){
						loadCurrentDataLine = true;
					}
				}
			}
			
			if(loadCurrentDataLine){
//				addSqlToMap(deleteMap, tableName, version, row, delSqlColIndex);
				addSqlToMap(updateBeforeDelMap, tableName, version, row, delSqlColIndex + 1);
				addSqlToMap(updateAfterInsertMap, tableName, version, row, delSqlColIndex + 2);
				addSqlToMap(insertMap, tableName, version, row, delSqlColIndex + 4);
				addDelSql(tableName, row, delSqlColIndex);
			}
		}
	}
	
	private void addDelSql(String tableName,  XSSFRow row, int index){
		if(!allDelMap.containsKey(tableName)){			
			allDelMap.put(tableName, new ArrayList<String>());
			
		}
		
		XSSFCell cell = row.getCell(index);
		if(cell != null){ //some tables havn't primary key
			String sql = evaluator.evaluate(cell).getStringValue();
			if(StringUtil.isNotEmpty(sql)){
				allDelMap.get(tableName).add(sql);
			}
		}
	}
	
	private void addSqlToMap(Map<String, Map<String,List<String>>> map, String tableName, String version, XSSFRow row, int index){
		if(map.containsKey(version)){
			if(!map.get(version).containsKey(tableName)){
				map.get(version).put(tableName, new ArrayList<String>());
			}
			
		}else{
			Map<String, List<String>> tableSqlMap = new HashMap<String, List<String>>();
			tableSqlMap.put(tableName, new ArrayList<String>());
			map.put(version, tableSqlMap);
		}
		
		XSSFCell cell = row.getCell(index);
		if(cell != null){ //some tables havn't primary key
			String sql = evaluator.evaluate(cell).getStringValue();
			if(StringUtil.isNotEmpty(sql)){
				map.get(version).get(tableName).add(sql);
			}
		}
	}
	
	private void initVersionTableMap(XSSFWorkbook book){
		XSSFSheet sheet = book.getSheet(Constants.VERION_TABLE_MAP_SHEET_NAME);
		XSSFRow row = null;
		XSSFCell cell = null;
		Integer version = 0;
		String tables = null;
		
		if(sheet == null){
			log.warn("The \"" + Constants.VERION_TABLE_MAP_SHEET_NAME + "\" doesn't exist. Please re-generate the schema excel.");
			return;
		}
		for(int i = 1 ; i <= sheet.getLastRowNum(); i++){
			row = sheet.getRow(i);
			cell = row.getCell(0);
			version = cell == null ? null : (int)cell.getNumericCellValue();
			
			cell = row.getCell(1);
			tables = cell == null ? "" : cell.getStringCellValue();
			
			if(version != null){
				if("".equals(tables.trim())){
					log.warn("The table names for data version(" + version + ") doesn't been specified.");
				}else{
					versionTableMap.put(version + "", tables);
				}				
			}
		}
	}
		
	private boolean isExist(String str1, String[] strArr){
		for(String s : strArr){
			if(s.trim().equalsIgnoreCase(str1)){
				return true;
			}
		}
		
		return false;
	}
	
	private void executeSqls(List<String> sqls, boolean reverse){
		if(sqls != null && !sqls.isEmpty() ){
			if(reverse){
				for(int i = sqls.size() - 1 ; i >= 0 ; i--){
					executeSql(sqls.get(i));
				}
			}else{
				for(String sql : sqls){				
					executeSql(sql);
				}
			}
		}
	}
	
	private void executeSql(String sql){
		log.debug(sql);
		if(sql.lastIndexOf(Constants.SEPARATE) == sql.length() - 1){
			sql = sql.substring(0,sql.length()-1);
		}
		dbConnect.executeSql(sql);
	}
		
	public void setSpecialProcessFile(String specialProcessFile){
		this.specialProcessFile = specialProcessFile;
	}

	public void setAuditTblPrefix(String auditTblPrefix) {
		this.auditTblPrefix = auditTblPrefix;
	}

	public void setAuditTblSuffix(String auditTblSuffix) {
		this.auditTblSuffix = auditTblSuffix;
	}
	
}
