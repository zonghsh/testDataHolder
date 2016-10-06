package org.rci.testdataholder.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.rci.testdataholder.Constants;
import org.rci.testdataholder.to.ColumnTO;
import org.rci.testdataholder.util.StringUtil;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DbConnect {	
	protected Logger log = Logger.getLogger(getClass());
	
	protected DataSource dataSource;
	
	protected String includeTablePrefix;
	
	protected String auditTblPrefix;
	
	protected String auditTblSuffix;
	
	protected String excludeTables;
	
	protected JdbcTemplate jdbcTemplate;
	
	protected Map<String, String> updateFkColMap;
	
	protected Map<String,List<String[]>> childParentMap; //key - child table name, value -- list of [parent tables; fk columns]
	protected Map<String, List<String>> parentChildMap;
	
	private List<String> childTableList;
	private List<String[]> circleFkReferenceList;
	
	public Map<String, List<ColumnTO>> retrieveTableColumnInfo(){
		Map<String, List<ColumnTO>> tableMap = new LinkedHashMap<String, List<ColumnTO>>();
		List<Map<String, Object>> list = retrieveTableColumnsList();
		if(list == null || list.isEmpty()){
			log.warn("No table has been created in the database. Or the username defined in common.properties isn't the owner of all tables.");
			return tableMap;
		}
		
		String key = "";
		Map<String, Object> map = null;
		ColumnTO ct = null;
		
		for (int i = 0; i < list.size(); i++) {
			map = list.get(i);
			key = (String) map.get(Constants.TABLE_NAME);
			if (!tableMap.containsKey(key)) {
				tableMap.put(key, new ArrayList<ColumnTO>());
			}
			ct = new ColumnTO();
			ct.setName((String) map.get(Constants.COLUMN_NAME));
			ct.setType((String) map.get(Constants.DATA_TYPE));
			//BigDecimal bd = (BigDecimal) map.get(Constants.DATA_LENGTH); //It is BigDecimal in Oracle and it is BigInteger in Mysql
			ct.setLength(map.get(Constants.DATA_LENGTH) == null ? "0" : map.get(Constants.DATA_LENGTH).toString());
			ct.setLength("-1".equals(ct.getLength()) ? "max" : ct.getLength()); //in mssql server, it'll be -1 if varchar(max)
			ct.setNullable((String) map.get(Constants.NULLABLE));
			ct.setIsPk((String)map.get(Constants.IS_PK));
			tableMap.get(key).add(ct);
		}
		
		log.debug("End getting new schema information.");
		return tableMap;
	}
	
	protected abstract List<Map<String, Object>> retrieveTableColumnsList();
	
	
	public String genValueForInsertSql(String columnType, String cellNm){
		StringBuffer value = new StringBuffer();
		value.append("IF(").append(cellNm).append("=\"\",\"\",\"");
		value.append(genEqValue(columnType, cellNm));
		value.append(",\")").append(Constants.SEPARATE);
		return value.toString();
	}
	
	public abstract String genEqValue(String columnType, String cellNm);
	
	
	public void executeSql(String sql){
		jdbcTemplate.execute(sql);
	}
	
	protected abstract List<Map<String, Object>> getAllTableNames();
	
	protected Map<String,List<String[]>> getChildParentMap(){
		if(childParentMap == null){
			childParentMap = retrieveChildAndParentTableInfo("N");
		}
		return childParentMap;
	}
	
	/**
	 * Sort the tables. The child table is after the parent table. This will be help to decide the order of executing the delete and insert sql
	 * @return 
	 */
	public String retrieveSortedTables(){
	    List<Map<String, Object>> allTables = getAllTableNames();
	    if(allTables == null || allTables.isEmpty()){
	    	return "";
	    }
	    List<String> tables = new ArrayList<String>();
	    for(Map<String, Object> map : allTables){
	    	tables.add((String)map.get(Constants.TABLE_NAME));
	    }
	        
	    
		//get parent and sub-table information which foreign key can't be null	
	    Map<String,List<String[]>> psMap = getChildParentMap();
				
		//put parent table to the top of the list
		log.debug("put parent table to the top of the list");
		List<String> tableNmList = new ArrayList<String>();
		childTableList = new ArrayList<String>();
		for(String childTblNm : psMap.keySet()){
			getOrderList(tableNmList,psMap,childTblNm); // the parent table is before child table
		}
		
		//key is table name, value is the list of all fk columns which need to be updated after insert sql
		Map<String,List<String[]>> nullabeFkChildParentTables = retrieveChildAndParentTableInfo("Y");
		List<String> nullableList = new ArrayList<String>();
		childTableList = new ArrayList<String>();
		circleFkReferenceList = new ArrayList<String[]>();
		for(String childTblNm : nullabeFkChildParentTables.keySet()){
			getOrderList(nullableList,nullabeFkChildParentTables,childTblNm); // the parent table is before child table
		}
		
		process(tableNmList, nullableList, nullabeFkChildParentTables);
		
		for(String tbl : tables){
			if(!tableNmList.contains(tbl)){
				tableNmList.add(tbl);
			}
		}
		
		StringBuffer sb = new StringBuffer();
		
		String[] excludeTbls = null;
		if(!StringUtil.isEmpty(excludeTables)){
			excludeTbls = excludeTables.split(Constants.SEPARATE);
		}
		String auditTbl = null;
		
		for(String tableNm : tableNmList){
						
			if(excludeTbls != null && isContain(excludeTbls, tableNm)){
				continue;
			}
			
			if(StringUtil.isEmpty(auditTblPrefix)){
				if(!StringUtil.isEmpty(auditTblSuffix)){
					auditTbl = tableNm + auditTblSuffix;
				}
			}else{
				auditTbl = auditTblPrefix + tableNm;				
			}
			if(isAuditTableExist(auditTbl)){
				sb.append(auditTbl).append(Constants.SEPARATE);
			}
			sb.append(tableNm).append(Constants.SEPARATE);
		}
		if(sb.length() > 0){
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}
	
	private boolean isContain(String[] array, String ele){
		for(String e : array){
			if(e.trim().equalsIgnoreCase(ele)){
				return true;
			}
		}
		return false;
	}
	
	protected abstract boolean isAuditTableExist(String auditTblNm);
	
	protected abstract List<Map<String, Object>> retrieveChildAndParentList(String fkIsNullable);
	
	private Map<String,List<String[]>> retrieveChildAndParentTableInfo(String fkIsNullable){
		List<Map<String, Object>> list = retrieveChildAndParentList(fkIsNullable);
		
		Map<String,List<String[]>> psMap = new LinkedHashMap<String,List<String[]>>();
		
		if(list == null || list.isEmpty()){
			log.info("There is no foreign key.");
			return psMap;
		}
		
		String[] info = null;
		String parentTbl = null;
		String fkCol = null;
		for(int i = 0 ; i < list.size() ; i++){
			Map<String, Object> map = list.get(i);
			String subTab = (String)map.get(Constants.TABLE_NAME);
			parentTbl = (String)map.get("PARENT_TABLE");
			fkCol = (String)map.get("FK_COL_NM");
			
			if(psMap.containsKey(subTab)){
				List<String[]> infoList = psMap.get(subTab);
				boolean flag = false;
				for(String[] arr : infoList){
					if(arr[0].equals(parentTbl)){ // multiple PK in parent table
						flag = true;
						arr[1] = arr[1] + Constants.SEPARATE + fkCol;
						break;
					}
				}
				if(!flag){
					info = new String[2];
					info[0] = parentTbl;
					info[1] = fkCol;
					infoList.add(info);
				}
				
			}else{
				psMap.put(subTab, new ArrayList<String[]>());
				info = new String[2];
				info[0] = parentTbl;
				info[1] = fkCol;
				psMap.get(subTab).add(info);
			}
		}
		return psMap;
	}
	
	private void process(List<String> notNullList, List<String> nullableList, Map<String,List<String[]>> nullabeFkChildParentTables){
		updateFkColMap = new HashMap<String, String>();
		
		Map<String, List<String>> updateFkChildParentMap = new HashMap<String, List<String>>();
		
		List<String[]> parents = null;
		for(String childTbl : nullabeFkChildParentTables.keySet()){
			parents = nullabeFkChildParentTables.get(childTbl);
			for(String[] parentInfo : parents){
				if(notNullList.contains(childTbl) && notNullList.contains(parentInfo[0])
						&& notNullList.indexOf(childTbl) < notNullList.indexOf(parentInfo[0])){//nullable child table is before the parent table.
					if(updateFkColMap.containsKey(childTbl)){
						if(updateFkColMap.get(childTbl).indexOf(parentInfo[1]) == -1){
							updateFkColMap.put(childTbl, updateFkColMap.get(childTbl) + Constants.SEPARATE + parentInfo[1]);
						}						
					}else{
						updateFkColMap.put(childTbl, parentInfo[1]);
					}
					if(!updateFkChildParentMap.containsKey(childTbl)){
						updateFkChildParentMap.put(childTbl, new ArrayList<String>());
					}
					updateFkChildParentMap.get(childTbl).add(parentInfo[0]);
				}
			}			
		}
		
		for(String[] arr : circleFkReferenceList){
			for(String child : nullabeFkChildParentTables.keySet()){
				for(String[] parentInfo : nullabeFkChildParentTables.get(child)){
					if(arr[0].equals(child) && arr[1].equals(parentInfo[0])){
						if(updateFkColMap.containsKey(child)){
							if(updateFkColMap.get(child).indexOf(parentInfo[1]) == -1){
								updateFkColMap.put(child, updateFkColMap.get(child) + Constants.SEPARATE + parentInfo[1]);
							}						
						}else{
							updateFkColMap.put(child, parentInfo[1]);
						}
					}
				}
			}
		}
		
		if(notNullList.isEmpty()){
			notNullList.addAll(nullableList);
			return;
		}
		//consider the scenario 1 and 2 in the DummyDyConnect to make sure the parent table is before the child table
		//and if there is circle FK reference
		
		String nullableChildTbl = null;
		int indexFront = notNullList.size() - 1;
		int indexBehind = 0;
		for(int i = nullableList.size() - 1 ; i >= 0; i--){
			nullableChildTbl = nullableList.get(i);
			if(notNullList.contains(nullableChildTbl)){
				int indexF = notNullList.indexOf(nullableChildTbl);	
				int indexB = indexF;
				if(updateFkChildParentMap.containsKey(nullableChildTbl)){
					for(String parent: updateFkChildParentMap.get(nullableChildTbl)){
						if(notNullList.contains(parent)){
							indexF = notNullList.indexOf(parent) < indexF ? notNullList.indexOf(parent) : indexF;
							indexB = notNullList.indexOf(parent) > indexB ? notNullList.indexOf(parent) : indexB;
						}
					}
				}		
				indexFront = indexFront < indexF ? indexFront : indexF;
				indexBehind = indexBehind > indexB ? indexBehind : indexB;
				
			}else{
				if(i > nullableList.indexOf(notNullList.get(indexBehind))){
					notNullList.add(indexBehind + 1, nullableChildTbl);
					
				}else{
					notNullList.add(indexFront, nullableChildTbl);
					indexBehind++; //one record is added, so the indexBehind need +1
				}				
			}
		}
	}
	
	private void getOrderList(List<String> tableNmList, Map<String,List<String[]>> psMap,String childTblNm){
		childTableList.add(childTblNm);
		List<String[]> parentList = psMap.get(childTblNm);
		if(parentList != null){
			for(String[] parentTab : parentList){
				if(!tableNmList.contains(parentTab[0])){
					if(parentTab[0].equals(childTblNm)){//has foreign key which refer to itself
						continue;
					}
					if(psMap.containsKey(parentTab[0])){
						if(childTableList.contains(parentTab[0])){ // there is circle fk reference.
							circleFkReferenceList.add(new String[]{childTblNm, parentTab[0]});
							continue;
						}
						getOrderList(tableNmList, psMap, parentTab[0]);
					}else{
						tableNmList.add(parentTab[0]);
					}
				}
			}
		}
		
		if(tableNmList.contains(childTblNm)){
			return;
		}
		tableNmList.add(childTblNm);
		
	}
	
	protected String excludeIncludeTablesSql(String tableCol){
		if(StringUtil.isEmpty(excludeTables) && StringUtil.isEmpty(includeTablePrefix)){
			return null;
		}
		
		StringBuffer sb = new StringBuffer();
		if(StringUtil.isNotEmpty(includeTablePrefix)){
			String[] prefixs = includeTablePrefix.split(Constants.SEPARATE);
			if(prefixs.length == 1){
				sb.append(" ").append(tableCol).append(" LIKE '").append(includeTablePrefix.trim()).append("%' ");
			}else{
				sb.append(" (");
				for(int i = 0; i < prefixs.length; i++){
					if(i > 0){
						sb.append("OR ");
					}
					sb.append(tableCol).append(" LIKE '").append(prefixs[i].trim()).append("%' ");
				}
				sb.append(") ");
			}
		}
		
		if(StringUtil.isNotEmpty(excludeTables)){
			if(sb.length() > 1){
				sb.append("AND ");
			}
			String[] excludes = excludeTables.split(Constants.SEPARATE);
			sb.append(tableCol).append(" NOT IN ('");
			for(int i = 0 ; i < excludes.length; i++){
				if(i > 0){
					sb.append(", '");
				}
				sb.append(excludes[i]).append("'");
			}
			sb.append(") ");			
		}
		
		return sb.toString();
	}
		
	public String retrieveChildTables(String parentTblNm){
		if(parentChildMap == null){
			Map<String,List<String[]>> childFatherMap = getChildParentMap();
			parentChildMap = new HashMap<String, List<String>>();
			for(String child : childFatherMap.keySet()){
				for(String[] info : childFatherMap.get(child)){
					if(!parentChildMap.containsKey(info[0])){
						parentChildMap.put(info[0], new ArrayList<String>());
					}
					parentChildMap.get(info[0]).add(child);
				}
			}
		}
		List<String> tbls = parentChildMap.get(parentTblNm);
		if(tbls == null || tbls.isEmpty()){
			return null;
		}
		StringBuffer sb = new StringBuffer();
		for(String child : tbls){
			sb.append(child).append(Constants.SEPARATE);
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}
	
	public Map<String, String> getUpdateFkColMap(){
		return updateFkColMap;
	}
	
	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * Only the tables with this prefix will be retrieved. E.g. if this value is "TBL_AA_; TB_", only the tables which prefix is
	 * "TBL_AA_" or "TB_" will be retrieved.
	 * @return
	 */
	public String getIncludeTablePrefix() {
		return includeTablePrefix;
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

	public String getAuditTblPrefix() {
		return auditTblPrefix;
	}

	public String getAuditTblSuffix() {
		return auditTblSuffix;
	}
	
	public String getExcludeTables() {
		return excludeTables;
	}

	public void setExcludeTables(String excludeTables) {
		this.excludeTables = excludeTables;
	}
	
	
}
