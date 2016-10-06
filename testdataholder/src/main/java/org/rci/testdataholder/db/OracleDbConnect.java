package org.rci.testdataholder.db;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.rci.testdataholder.Constants;
import org.rci.testdataholder.util.StringUtil;
import org.springframework.jdbc.core.JdbcTemplate;

public class OracleDbConnect extends DbConnect{	
	
	public OracleDbConnect(DataSource dataSource, String includeTablePrefix){
		this.dataSource = dataSource;
		this.includeTablePrefix = includeTablePrefix;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}
	
	public OracleDbConnect(DataSource dataSource){
		this.dataSource = dataSource;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}
		
	protected List<Map<String, Object>> retrieveTableColumnsList(){
		log.debug("Begin getting new schema information.");		
		//When drop one table, there is some useless info in USER_TAB_COLUMNS
		String clean = "purge recyclebin";
		jdbcTemplate.execute(clean);
		
		StringBuffer sql = new StringBuffer();
		sql.append("SELECT A.*, B.IS_PK FROM ")
		   .append("( SELECT UTC.TABLE_NAME,UTC.COLUMN_NAME,UTC.DATA_TYPE,UTC.DATA_LENGTH,UTC.NULLABLE,UTC.COLUMN_ID ")
		   .append("  FROM  USER_TAB_COLUMNS UTC ");
	    
	    String exculdeInclude =  excludeIncludeTablesSql("TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" WHERE ").append(exculdeInclude);
		}
		
		sql.append(") A LEFT JOIN ")
		   .append("( SELECT CU.TABLE_NAME, CU.COLUMN_NAME, 'Y' IS_PK ")
		   .append("  FROM USER_CONS_COLUMNS CU, USER_CONSTRAINTS AU ")
		   .append("  WHERE CU.CONSTRAINT_NAME = AU.CONSTRAINT_NAME AND AU.CONSTRAINT_TYPE = 'P' ")
		   .append(") B ")
		   .append("ON A.TABLE_NAME = B.TABLE_NAME AND A.COLUMN_NAME = B.COLUMN_NAME ")
		   .append("ORDER BY A.TABLE_NAME,A.COLUMN_NAME ");
		
		return jdbcTemplate.queryForList(sql.toString());
	}
		
	protected List<Map<String, Object>> getAllTableNames(){
		StringBuffer sql = new StringBuffer();
		sql.append("SELECT DISTINCT TABLE_NAME FROM USER_TAB_COLUMNS ");
		
	    String exculdeInclude =  excludeIncludeTablesSql("TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" WHERE ").append(exculdeInclude);
		}
	    
	    return jdbcTemplate.queryForList(sql.toString());
	}
	
	protected  List<Map<String, Object>> retrieveChildAndParentList(String fkIsNullable){
		StringBuffer sql = new StringBuffer();
		
		sql.append("SELECT DISTINCT A.TABLE_NAME , B.TABLE_NAME PARENT_TABLE, D.COLUMN_NAME FK_COL_NM, D.NULLABLE ")
//		sql.append("SELECT DISTINCT A.TABLE_NAME , B.TABLE_NAME PARENT_TABLE ")
		   .append("FROM USER_CONSTRAINTS A,USER_CONSTRAINTS B, ALL_CONS_COLUMNS CONS, USER_TAB_COLUMNS D ")
		   .append("WHERE A.CONSTRAINT_TYPE = 'R' AND B.CONSTRAINT_TYPE = 'P' AND A.R_CONSTRAINT_NAME = B.CONSTRAINT_NAME ")
		   .append("AND CONS.CONSTRAINT_NAME = A.CONSTRAINT_NAME ")
		   .append("AND D.TABLE_NAME = A.TABLE_NAME AND D.COLUMN_NAME = CONS.COLUMN_NAME ")
		   .append("AND D.NULLABLE = '").append(fkIsNullable).append("' "); //ignore the foreign key can be null. It will effect the insert and delete order when it isn't null
						
		String exculdeInclude =  excludeIncludeTablesSql("A.TABLE_NAME");
		if(exculdeInclude != null){
			sql.append(" AND ").append(exculdeInclude);
		}
		
		return jdbcTemplate.queryForList(sql.toString());
	}
	
	public String genEqValue(String columnType, String cellNm){
		StringBuffer value = new StringBuffer();
		if(columnType.indexOf("CHAR") != -1){
			value.append("'\"&").append(cellNm).append("&\"'");
			
		}else if("DATE".equalsIgnoreCase(columnType)){
			value.append("TO_DATE('\"&").append(cellNm).append("&\"','YYYY-MM-DD')");
			
		}else if("TIMESTAMP".equalsIgnoreCase(columnType)){
			value.append("TO_DATE('\"&").append(cellNm).append("&\"','YYYY-MM-DD HH24:MI:SS')");
			
		}else if("NUMBER".equalsIgnoreCase(columnType)){							
			value.append("\"&").append(cellNm).append("&\"");
		}
		return value.toString();
	}
	
	protected boolean isAuditTableExist(String auditTblNm){
		if(StringUtil.isEmpty(auditTblNm)){
			return false;
		}
		String sql = "select TABLE_NAME FROM USER_TAB_COLUMNS  where TABLE_NAME = '" + auditTblNm + "'";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
		return list != null && !list.isEmpty();
	}
	
}
