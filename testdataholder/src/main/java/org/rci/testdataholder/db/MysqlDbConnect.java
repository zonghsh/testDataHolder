package org.rci.testdataholder.db;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.rci.testdataholder.Constants;
import org.rci.testdataholder.util.StringUtil;
import org.springframework.jdbc.core.JdbcTemplate;

public class MysqlDbConnect extends DbConnect{
	public MysqlDbConnect(DataSource dataSource, String includeTablePrefix){
		this.dataSource = dataSource;
		this.includeTablePrefix = includeTablePrefix;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}
	
	public MysqlDbConnect(DataSource dataSource){
		this.dataSource = dataSource;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}

	@Override
	protected List<Map<String, Object>> retrieveTableColumnsList() {
		StringBuffer sql = new StringBuffer();
		sql.append("select TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH AS DATA_LENGTH, IS_NULLABLE, ")
		   .append("COLUMN_KEY from information_schema.COLUMNS ");
		
		String exculdeInclude =  excludeIncludeTablesSql("TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" WHERE ").append(exculdeInclude);
		}
		
		sql.append("ORDER BY TABLE_NAME, COLUMN_NAME ");
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString());
		
		if(list != null && !list.isEmpty()){
			String isNullable = null;
			String isPk = null;
			for(Map<String, Object> map : list){
				isNullable = (String)map.get("IS_NULLABLE");
				map.put(Constants.NULLABLE, "YES".equalsIgnoreCase(isNullable) ? "Y" : "N");
				map.remove("IS_NULLABLE");
				
				isPk = (String)map.get("COLUMN_KEY");
				map.put(Constants.IS_PK, "PRI".equalsIgnoreCase(isPk) ? "Y" : "N");
				map.remove("COLUMN_KEY");
			}
		}
		return list;
	}

	@Override
	protected List<Map<String, Object>> getAllTableNames() {
		StringBuffer sql = new StringBuffer();
		sql.append("select DISTINCT TABLE_NAME from information_schema.COLUMNS ");
		
		String exculdeInclude =  excludeIncludeTablesSql("TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" WHERE ").append(exculdeInclude);
		}
		
		return jdbcTemplate.queryForList(sql.toString());
	}

	@Override
	protected List<Map<String, Object>> retrieveChildAndParentList(String fkIsNullable) {
		StringBuffer sql = new StringBuffer();
		sql.append("SELECT DISTINCT A.TABLE_NAME , A.referenced_table_name PARENT_TABLE, A.COLUMN_NAME FK_COL_NM, B.IS_NULLABLE ")
		   .append("FROM information_schema.KEY_COLUMN_USAGE A, information_schema.COLUMNS B ")
		   .append("WHERE A.referenced_table_name is not null ")
		   .append("AND a.TABLE_NAME = b.TABLE_NAME ")
		   .append("AND a.COLUMN_NAME = b.COLUMN_NAME ")
		   .append("AND B.IS_NULLABLE = '").append("Y".equalsIgnoreCase(fkIsNullable) ? "YES" : "NO").append("' "); //ignore the foreign key can be null. It will effect the insert and delete order when it isn't null
		
		
		String exculdeInclude =  excludeIncludeTablesSql("A.TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" AND ").append(exculdeInclude);
		}
		
		return jdbcTemplate.queryForList(sql.toString());
	}
	
	public String genEqValue(String columnType, String cellNm){
		StringBuffer value = new StringBuffer();
		columnType = columnType.toUpperCase();
		if(columnType.indexOf("CHAR") != -1){
			value.append("'\"&").append(cellNm).append("&\"'");
			
		}else if("DATE".equals(columnType)){
			value.append("STR_TO_DATE('\"&").append(cellNm).append("&\"','%Y-%m-%D')");
			
		}else if("TIME".equals(columnType)){
			value.append("STR_TO_DATE('\"&").append(cellNm).append("&\"','%T')");
			
		}else if("YEAR".equals(columnType)){
			value.append("STR_TO_DATE('\"&").append(cellNm).append("&\"','%Y')");
			
		}else if("DATETIME".equals(columnType) || "TIMESTAMP".equals(columnType)){
			value.append("STR_TO_DATE('\"&").append(cellNm).append("&\"','%Y-%m-%D %T')");
			
		}else if(columnType.indexOf("INT") != -1 || "FLOAT".equals(columnType) || "DOUBLE".equals(columnType) || "DECIMAL".equals(columnType)){							
			value.append("\"&").append(cellNm).append("&\"");
		}
		return value.toString();
	}
	
	protected boolean isAuditTableExist(String auditTblNm){
		if(StringUtil.isEmpty(auditTblNm)){
			return false;
		}
		String sql = "select TABLE_NAME FROM information_schema.TABLES  where TABLE_NAME = '" + auditTblNm + "'";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
		return list != null && !list.isEmpty();
	}

}
