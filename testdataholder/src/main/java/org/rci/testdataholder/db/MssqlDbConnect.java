/*
 * Copyright @ 2006-2016 NCS Pte. Ltd. All Rights Reserved
 *
 * This software is confidential and proprietary to NCS Pte. Ltd. You shall
 * use this software only in accordance with the terms of the license
 * agreement you entered into with NCS. No aspect or part or all of this
 * software may be reproduced, modified or disclosed without full and
 * direct written authorisation from NCS.
 *
 * NCS SUPPLIES THIS SOFTWARE ON AN "AS IS" BASIS. NCS MAKES NO
 * REPRESENTATIONS OR WARRANTIES, EITHER EXPRESSLY OR IMPLIEDLY, ABOUT THE
 * SUITABILITY OR NON-INFRINGEMENT OF THE SOFTWARE. NCS SHALL NOT BE LIABLE
 * FOR ANY LOSSES OR DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 *
 */
package org.rci.testdataholder.db;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.rci.testdataholder.Constants;
import org.rci.testdataholder.util.StringUtil;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 
 * @author huangjian
 * @version 1
 *
 */
public class MssqlDbConnect extends DbConnect {
	
	public MssqlDbConnect(DataSource dataSource, String includeTablePrefix){
		this.dataSource = dataSource;
		this.includeTablePrefix = includeTablePrefix;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}
	
	public MssqlDbConnect(DataSource dataSource){
		this.dataSource = dataSource;
		jdbcTemplate  = new JdbcTemplate(dataSource);
	}

	@Override
	protected List<Map<String, Object>> retrieveTableColumnsList() {
		
//		select d.name TABLE_NAME, a.name COLUMN_NAME,
//    case when exists (select 1 from SysObjects where xtype='PK' and parent_obj = a.id
//				and name in ( select name from SysIndexes where indid in 
//								(select indid from Sysindexkeys WHERE id = a.id and colid=a.colid)))
//		THen 'Y' else 'N' end IS_PK, 
//		b.name DATA_TYPE, a.length, COLUMNPROPERTY(a.id, a.name, 'PRECISION'), 
//		isnull(COLUMNPROPERTY(a.id, a.name, 'Scale'), 0) xiaoshu,
//		case when a.isnullable = 1 then 'Y' else 'N' end IS_NULLABLE
//from SysColumns a
//left join SysTypes b on a.xusertype = b.xusertype
//inner join SysObjects d on a.id = d.id and d.xtype='U' and d.name <> 'dtproperties'
//order by d.name, a.name
		StringBuffer sql = new StringBuffer();
		sql.append("select d.name TABLE_NAME, a.name COLUMN_NAME, b.name DATA_TYPE, a.length AS DATA_LENGTH, ")
		   .append("   case when a.isnullable = 1 then 'Y' else 'N' end IS_NULLABLE, ")
		   .append("   case when exists (select 1 from SysObjects where xtype='PK' and parent_obj = a.id ")
		   .append("                         and name in ( select name from SysIndexes where indid in ")
		   .append("                                        (select indid from Sysindexkeys WHERE id = a.id and colid=a.colid)))")
		   .append("        THen 'Y' else 'N' end IS_PK ")		   
		   .append("from SysColumns a ")
		   .append("left join SysTypes b on a.xusertype = b.xusertype ")
		   .append("inner join SysObjects d on a.id = d.id and d.xtype='U' and d.name <> 'dtproperties' ");
				
		String exculdeInclude =  excludeIncludeTablesSql("d.name");
		if(exculdeInclude != null){
			sql.append(" WHERE ").append(exculdeInclude);
		}
		
		sql.append("ORDER BY d.name, a.name ");
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString());
		
		if(list != null && !list.isEmpty()){
			String isNullable = null;
			for(Map<String, Object> map : list){
				isNullable = (String)map.get("IS_NULLABLE");
				map.put(Constants.NULLABLE, isNullable);
				map.remove("IS_NULLABLE");				
			}
		}
		return list;
	}

	@Override
	public String genEqValue(String columnType, String cellNm) {
		StringBuffer value = new StringBuffer();
		columnType = columnType.toUpperCase();
		if(columnType.indexOf("CHAR") != -1){
			value.append("'\"&").append(cellNm).append("&\"'");
			
		}else if("DATE".equals(columnType)){
			value.append("CONVERT(date, \"&IF(ISNUMBER(SEARCH(\"getdate\", ").append(cellNm)
				 .append(")), \"CONVERT(varchar,\"&").append(cellNm).append("&\",111)\",\"'\"&")
				 .append(cellNm).append("&\"'\")&\",111)"); //111 - yyyy/mm/dd
									
		}else if("DATETIME".equals(columnType) || "TIMESTAMP".equals(columnType)){
			value.append("CONVERT(datetime, \"&IF(ISNUMBER(SEARCH(\"getdate\", ").append(cellNm)
			     .append(")), \"CONVERT(varchar,\"&").append(cellNm).append("&\",120)\",\"'\"&")
			     .append(cellNm).append("&\"'\")&\",120)"); //120 - yyyy-mm-dd HH:MM:ss
						
		}else if(columnType.indexOf("INT") != -1 || "FLOAT".equals(columnType) 
					|| "DOUBLE".equals(columnType) || "DECIMAL".equals(columnType)
					|| "BIT".equals(columnType)){							
			value.append("\"&").append(cellNm).append("&\"");
		}
		return value.toString();
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
		/*select 
			object_name(f.parent_object_id) child_table_name,
			object_name(f.referenced_object_id) parent_table_name,
			c.name child_col_name, d.name parent_col_name, c.isnullable
		from sysobjects s 
		inner join sysforeignkeys k on k.constid = s.id
		inner join syscolumns c on c.id=k.fkeyid and k.fkey=c.colid
		inner join syscolumns d on d.id=k.rkeyid and d.colid=k.rkey
		inner join sys.foreign_keys f on s.name=f.name
		where s.xtype='F'*/	
	
		sql.append("SELECT DISTINCT object_name(f.parent_object_id) TABLE_NAME , object_name(f.referenced_object_id) PARENT_TABLE,  ")
		   .append("       c.name FK_COL_NM, c.isnullable IS_NULLABLE ")
		   .append("FROM sysobjects s ")
		   .append("inner join sysforeignkeys k on k.constid = s.id ")
		   .append("inner join syscolumns c on c.id=k.fkeyid and k.fkey=c.colid ")
		   .append("inner join sys.foreign_keys f on s.name=f.name ")
		   .append("where s.xtype='F' ")
		   .append("AND c.isnullable = ").append("Y".equalsIgnoreCase(fkIsNullable) ? 1 : 0).append(" "); //ignore the foreign key can be null. It will effect the insert and delete order when it isn't null
				
		String exculdeInclude =  excludeIncludeTablesSql("object_name(f.parent_object_id)");
		if(exculdeInclude != null){
			sql.append(" AND ").append(exculdeInclude);
		}
		
		return jdbcTemplate.queryForList(sql.toString());
	}
	
	protected boolean isAuditTableExist(String auditTblNm){
		if(StringUtil.isEmpty(auditTblNm)){
			return false;
		}
		String sql = "select name FROM sysobjects  where xtype = 'U' and name = '" + auditTblNm + "'";
		List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
		return list != null && !list.isEmpty();
	}

}
