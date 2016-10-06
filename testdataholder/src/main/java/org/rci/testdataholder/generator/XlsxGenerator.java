package org.rci.testdataholder.generator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rci.testdataholder.Constants;
import org.rci.testdataholder.db.DbConnect;
import org.rci.testdataholder.to.ColumnTO;
import org.rci.testdataholder.util.StringUtil;

public class XlsxGenerator implements Generator{
	
	private Logger log = Logger.getLogger(getClass());
	
	private boolean isUpdate = false;
	
	private String generatedFilePath;
	
	private DbConnect dbConnect;
	
	private XSSFFont headFont;
	private XSSFCellStyle contentCs;
	private XSSFCellStyle headStyle;
	private XSSFCellStyle linkStyle;
	private XSSFCellStyle pkcs;
	
	private Map<String, String> updateFkColMap;
	
	//key - table name, value - update fk sql
	private Map<String, String> updateBeforeDeleteMap = new HashMap<String, String>();
	
	public XlsxGenerator(String generatedFilePath, DbConnect dbInfo, boolean isUpdate){
		this.generatedFilePath = generatedFilePath;
		this.dbConnect = dbInfo;
		this.isUpdate = isUpdate;
	}

	public void generateSchema(String path) throws Exception {
		InputStream ins = null;
		FileOutputStream fileOut = null;
		XSSFWorkbook oldBook = null;
		XSSFWorkbook book = null;
		try{
			book = new XSSFWorkbook();
			XSSFSheet indSheet = book.createSheet(Constants.INDEX_SHEET_NAME);	//To make sure the "INDEX" sheet is the first sheet
			
			initFontAndStyle(book);
			
			Map<String,Integer> oldColNumMap = new HashMap<String,Integer>();// key - table name;
			Map<String,String> staffMap = new LinkedHashMap<String,String>();// key - table name;
			Map<Integer,String> versionTablesMap = new HashMap<Integer,String>();
			
			if(isUpdate){
				log.debug("Get old schema information.");
				ins = new FileInputStream(generatedFilePath);
				
				oldBook = new XSSFWorkbook(ins);
				XSSFSheet oldIndSheet = oldBook.getSheet(Constants.INDEX_SHEET_NAME);
				XSSFRow row = null;
				String staffNm = null;
				String tblNm = null;
				XSSFCell cell = null;
				
				for(int i = 1 ; i <= oldIndSheet.getLastRowNum(); i++){
					row = oldIndSheet.getRow(i);
					cell = row.getCell(0); //table name column
					if(cell == null){
						continue;
					}
					tblNm = cell.getStringCellValue();
					
					cell = row.getCell(1); //sql column index
					Integer sqlIndex = cell == null ? null : (int)cell.getNumericCellValue();
					if(tblNm != null && !"".equals(tblNm.trim())){
						oldColNumMap.put(tblNm.trim().toUpperCase(), sqlIndex);
					}
					
					cell = row.getCell(4);
					staffNm = cell == null ? null : cell.getStringCellValue();
					
					cell = row.getCell(5);
					String series = cell == null ? null : cell.getStringCellValue();
					if(staffNm != null && !"".equals(staffNm.trim())){
						staffMap.put(staffNm, series);
					}					
				}
				
				XSSFSheet versionTblSheet = oldBook.getSheet(Constants.VERION_TABLE_MAP_SHEET_NAME);
				if(versionTblSheet != null){
					Integer version = 0;
					String tables = null;
					for(int i = 1 ; i <= versionTblSheet.getLastRowNum(); i++){
						row = versionTblSheet.getRow(i);
						cell = row.getCell(0);
						version = cell == null ? null : (int)cell.getNumericCellValue();
						
						cell = row.getCell(1);
						tables = cell == null ? "" : cell.getStringCellValue();
						
						if(version != null && !"".equals(tables.trim())){
							versionTablesMap.put(version, tables);
						}
					}
				}
			}
						
			Map<String, List<ColumnTO>> tableMap = dbConnect.retrieveTableColumnInfo();
			fillInfoToIndexSheet(indSheet, tableMap, staffMap);
			
			createVersionTableMapSheet(book, versionTablesMap);
						
			updateFkColMap = dbConnect.getUpdateFkColMap(); // the updateFkColMap was initialized in dbConnect.retrieveSortedTables();
			
			for(String key : tableMap.keySet()){
				XSSFSheet sheet = book.createSheet(key);
				XSSFRow colNmRow = sheet.createRow(0);
				XSSFRow colTypeRow = sheet.createRow(1);
				XSSFRow nullableRow = sheet.createRow(2);
				XSSFRow contentRow = sheet.createRow(Constants.FIRST_CONTENT_ROW_IND);
				
				XSSFCell c1 = colNmRow.createCell(0);
				c1.setCellValue("Column Name");
				c1.setCellStyle(headStyle);
				XSSFCell c2 = colTypeRow.createCell(0);
				c2.setCellValue("Data Type");
				c2.setCellStyle(headStyle);
				XSSFCell c3 = nullableRow.createCell(0);
				c3.setCellValue("Mandatory");
				c3.setCellStyle(headStyle);
				contentRow.createCell(0).setCellStyle(contentCs);
				contentRow.getCell(0).setCellType(XSSFCell.CELL_TYPE_STRING);
				
				sheet.autoSizeColumn(0);
				List<ColumnTO> columns = tableMap.get(key);
				int col = 1; //the db column info is from B column in excel
				
				String type = null;
				for(ColumnTO colTo : columns){
					String nmStr = colTo.getName();
					type = colTo.getType().toUpperCase();
					String typeLen = type + (type.indexOf("CHAR") == -1 ? "" : "("+colTo.getLength()+")");
					XSSFCell nmCell = colNmRow.createCell(col);
					nmCell.setCellValue(nmStr);
					nmCell.setCellStyle(headStyle);
					
					XSSFCell typeCell = colTypeRow.createCell(col);
					typeCell.setCellValue(typeLen);
					typeCell.setCellStyle(headStyle);
					
					XSSFCell nullCell = nullableRow.createCell(col);
					String nullable = colTo.getNullable();
					nullCell.setCellValue("Y".equals(nullable)?"N" : "Y");
					nullCell.setCellStyle(headStyle);	
					contentRow.createCell(col).setCellStyle(contentCs);
					contentRow.getCell(col).setCellType(XSSFCell.CELL_TYPE_STRING);
					
					sheet.autoSizeColumn(col);
					col++;
				}
				
				int lastRow = Constants.FIRST_CONTENT_ROW_IND; // the first 3 rows are "Column Name", "Data Type" and "Mandatory". So the data is from row 3.
				if(isUpdate){
					log.debug("Copy old data in " + key + " to new excel.");
					moveOldValue2NewTable(oldBook,oldColNumMap,sheet,tableMap.get(key).size()+2,contentCs);
					XSSFSheet oldSheet = oldBook.getSheet(sheet.getSheetName());
					if(oldSheet != null) //if added one new table
						lastRow = oldSheet.getLastRowNum();
				}
				
				generateSqlCells(sheet,columns,lastRow);
				
				//set freeze pane
				sheet.createFreezePane(1, Constants.FIRST_CONTENT_ROW_IND);
				
			}
			
			fillUpdateSqlColumnInIndexSheet(indSheet);
		    
		    log.debug("Create hyperlink for each sheet.");
			
			//write to file
			fileOut = new FileOutputStream(path);
		    book.write(fileOut);
		    fileOut.close();
		    
		}catch(Exception e){
			log.error(e);
			throw e;
		}finally{
			if(ins != null){
				ins.close();
			}
			if(fileOut != null){
				fileOut.close();
			}
		}
	}
	
	private void fillUpdateSqlColumnInIndexSheet(XSSFSheet indSheet){
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
			
			if(updateBeforeDeleteMap.containsKey(tblNm)){
				cell = row.createCell(Constants.UPDATE_SQL_COL_IND);
				cell.setCellValue(updateBeforeDeleteMap.get(tblNm));
				cell.setCellStyle(pkcs);
			}
			
		}
		//indSheet.autoSizeColumn(Constants.UPDATE_SQL_COL_IND);
	}
	
	private void createVersionTableMapSheet(XSSFWorkbook book, Map<Integer, String> versionTablesMap){
		XSSFSheet sheet = book.createSheet(Constants.VERION_TABLE_MAP_SHEET_NAME);
		XSSFRow headerRow = sheet.createRow(0);
		createHeaderCell(headerRow, 0, "Data Version");
		createHeaderCell(headerRow, 1, "Table Names");
		
		int rowInd = 1;
		XSSFRow row = null;
		XSSFCell cell = null;
		for(Integer version : versionTablesMap.keySet()){
			row = sheet.createRow(rowInd);
			cell = row.createCell(0);
			cell.setCellValue(version);
			
			cell = row.createCell(1);
			cell.setCellValue(versionTablesMap.get(version));
			rowInd++;
		}
		
		sheet.autoSizeColumn(0);
		sheet.autoSizeColumn(1);
	}
	
	private void createHeaderCell(XSSFRow headerRow, int index, String value){
		XSSFCell headerCell = headerRow.createCell(index);
	    headerCell.setCellStyle(headStyle);
	    headerCell.setCellValue(value);
	}
	
	private void fillInfoToIndexSheet(XSSFSheet indSheet, Map<String, List<ColumnTO>> tableMap, Map<String,String> staffMap){
		XSSFWorkbook book = indSheet.getWorkbook();
		XSSFCreationHelper createHelper = book.getCreationHelper();
	    
		XSSFRow headerRow = indSheet.createRow(0);		
		createHeaderCell(headerRow, 0, "Table Name");
		createHeaderCell(headerRow, 1, "Sql Column Index");
		createHeaderCell(headerRow, 2, "Primary Key Column");
		createHeaderCell(headerRow, 4, "Staff Name");
		createHeaderCell(headerRow, 5, "Begin Series");
		createHeaderCell(headerRow, Constants.SORTED_TABLE_COL_IND, "Sorted Tables");
		createHeaderCell(headerRow, Constants.UPDATE_SQL_COL_IND, "Update Sql");
		createHeaderCell(headerRow, Constants.UPDATE_SQL_COL_IND + 1, "Child Tables");
	    
	    int i = 1 ;
		XSSFCell cell = null;
		XSSFHyperlink link = null;
		XSSFCell colNumCell = null;
		XSSFCell pkCell = null;
		XSSFRow row = null;
		
		for(String key : tableMap.keySet()){
			row = indSheet.createRow(i);
			//create table name hyperlink
			cell = row.createCell(0);
			cell.setCellValue(key);
			link = createHelper.createHyperlink(XSSFHyperlink.LINK_DOCUMENT);
			link.setAddress("'" + key + "'!A1");
			cell.setHyperlink(link);
			cell.setCellStyle(linkStyle);
			
			//create number of table's columns. used for getting the insert SQL position
			colNumCell = row.createCell(1);
			colNumCell.setCellValue(tableMap.get(key).size() + 2);
			colNumCell.setCellStyle(contentCs);
			
			//create pk columns of the table
			pkCell = row.createCell(Constants.PK_COL_IND);
			pkCell.setCellValue(getPkColumns(tableMap, key));
			pkCell.setCellStyle(pkcs);
			
			//create Child Tables
			cell = row.createCell(Constants.UPDATE_SQL_COL_IND + 1);
			String childs = dbConnect.retrieveChildTables(key);
			childs = childs == null ? " " : childs;
			cell.setCellValue(childs);
			
			i++;
		}
		
		if(isUpdate){ // add the staff name info
			String series = null;
			i = 1;
			for(String staffNm : staffMap.keySet()){
				row = indSheet.getRow(i);
				if(row == null){
					row = indSheet.createRow(i);
				}
				
				cell = row.createCell(4); //staff name column
				cell.setCellValue(staffNm);
				cell.setCellStyle(pkcs);
				
				series = staffMap.get(staffNm);
				if(series != null){
					cell = row.createCell(5); //begin series column
					cell.setCellValue(series);
					cell.setCellStyle(pkcs);
				}
				
				i++;
			}
		}
		
		//create sortedTable Cell
		cell = indSheet.getRow(0).createCell(Constants.SORTED_TABLE_COL_IND + 1); //put it behind "Sorted Tables"
		cell.setCellStyle(pkcs);
		cell.setCellValue(dbConnect.retrieveSortedTables());
		
		addNotePart(indSheet);
		
		indSheet.autoSizeColumn(0);
		indSheet.autoSizeColumn(1);
		indSheet.autoSizeColumn(2);
		indSheet.autoSizeColumn(4);
		indSheet.autoSizeColumn(5);
		indSheet.autoSizeColumn(7);
		
	}
	
	
	private void addNotePart(XSSFSheet indSheet){
		int noteRowInd = 2;
		XSSFRow row = indSheet.getRow(noteRowInd); 
		if(row == null){
			row = indSheet.createRow(2);
		}
				
		XSSFCell cell = row.createCell(Constants.SORTED_TABLE_COL_IND); //put the "Note" at the same column of "Sorted Tables"
		cell.setCellStyle(headStyle);
		cell.setCellValue("Note");
		
		createCell(indSheet, noteRowInd,      "1. Don't change any value(except \"Staff Name\" and \"Begin Series\" columns) in this sheet.");
		createCell(indSheet, noteRowInd + 1,  "2. \"Staff Name\" and \"Begin Series\" columns used to assign one integer range to one staff.");
		createCell(indSheet, noteRowInd + 2,  "     It will help to know who prepared the data and avoid conflict.");
		createCell(indSheet, noteRowInd + 3,  "3. If the \"data type\" is DATETIME or Timestamps, please use \"YYYY-MM-DD H24:mm:SS\" format as the value.");
		createCell(indSheet, noteRowInd + 4,  "    If it is DATE, please use \"YYYY-MM-DD\"(Use \"mm/dd/yyyy\" if database is ms-sql server).");
		createCell(indSheet, noteRowInd + 5,  "4. The CRUD sql is only generated in row 4. So when you prepare the data below row 4, ");
		createCell(indSheet, noteRowInd + 6,  "    you should copy the whole row 4 and paste it in new row.");
		createCell(indSheet, noteRowInd + 7,  "5. The data version for one test case should be same even if they are in different table.");
		createCell(indSheet, noteRowInd + 8,  "6. The \"Version Table Map\" sheet should be specified.");
		createCell(indSheet, noteRowInd + 9,  "     E.g. You prepared data which version is 100 in TABLE_1 and TABLE_2.");
		createCell(indSheet, noteRowInd + 10,  "     You should add one row in \"Version Table Map\" sheet.");
		createCell(indSheet, noteRowInd + 11,  "     The value in \"Data Version\" column should be 100 and the value in \"Table Names\" should be \"TABLE_1;TABLE_2\".");
		
	}
	
	private void createCell(XSSFSheet indSheet, int rowInd, String value){
		XSSFRow row = indSheet.getRow(rowInd);
		if(row == null){
			row = indSheet.createRow(rowInd);
		}
		
		XSSFCell cell = row.createCell(Constants.SORTED_TABLE_COL_IND + 1);
		cell.setCellValue(value);
	}
	
	private String getPkColumns(Map<String, List<ColumnTO>> tableMap, String table){
		List<ColumnTO> columns = tableMap.get(table);
		StringBuffer pks = new StringBuffer();
		for(ColumnTO ct : columns){
			if("Y".equalsIgnoreCase(ct.getIsPk())){
				pks.append(ct.getName()).append(",");
			}
		}
		if(pks.indexOf(",") != -1){
			pks.deleteCharAt(pks.length()-1);
		}
		return pks.toString();
	}
	
	private  void moveOldValue2NewTable(XSSFWorkbook oldBook,Map<String,Integer> oldColNumMap,
			XSSFSheet newSheet,int newSheetColNm, XSSFCellStyle contentCs){
		String sheetNm = newSheet.getSheetName().trim().toUpperCase();
		XSSFSheet oldSheet = oldBook.getSheet(sheetNm);
		if(oldSheet == null)
			return;
		
		XSSFRow oldHeadRow = oldSheet.getRow(0);
		XSSFRow newHeadRow = newSheet.getRow(0);
		for(int oldCol = 0 ; oldCol < oldColNumMap.get(sheetNm)-1 ; oldCol++){
			String oldColNm = oldHeadRow.getCell(oldCol).getStringCellValue();
			for(int newCol = 0 ; newCol < newSheetColNm - 1 ; newCol++){
				String newColNm = newHeadRow.getCell(newCol).getStringCellValue();
				if(oldColNm.equals(newColNm)){
					copyOld2New(oldSheet,newSheet,oldCol,newCol,contentCs);
					break;
				}
			}
		}
	}
	
	private  void copyOld2New(XSSFSheet oldSheet, XSSFSheet newSheet,int oldCol, int newCol, XSSFCellStyle contentCs){
		for(int row = Constants.FIRST_CONTENT_ROW_IND ; row <= oldSheet.getLastRowNum(); row++){
			XSSFCell oldCell = oldSheet.getRow(row).getCell(oldCol);
			if(oldCell == null){
				continue;
			}
			XSSFRow newRow = newSheet.getRow(row);
			if(newRow == null){
				newRow = newSheet.createRow(row);
			}
			XSSFCell newCell = newRow.createCell(newCol);
			newCell.setCellStyle(contentCs);
			int oldCellType = oldCell.getCellType();
			if(XSSFCell.CELL_TYPE_NUMERIC == oldCellType){
				if(DateUtil.isCellDateFormatted(oldCell)){
					newCell.setCellValue(oldCell.getDateCellValue());
				}else{
					newCell.setCellValue(oldCell.getNumericCellValue());
				}
			}else {
				newCell.setCellValue(oldCell.getStringCellValue());
			}
		}
	}
	
	private  void generateSqlCells(XSSFSheet sheet,List<ColumnTO> columns,int lastRow){
		String tableNm = sheet.getSheetName();
		log.debug("Begin generate DELETE, UPDATE and INSERT sql formula in " + tableNm + " sheet.");
		
		int delSqlColIndex = columns.size() + 2;
		
		for(int rowIndex = Constants.FIRST_CONTENT_ROW_IND ; rowIndex <= lastRow ; rowIndex++){
			int rowLbl = rowIndex + 1;
			//generate delete sql
			StringBuffer delSql = new StringBuffer();
			delSql.append("\"DELETE FROM ").append(tableNm);
			
			StringBuffer where = new StringBuffer();
			
			//generate insert sql
			StringBuffer colNm = new StringBuffer();
			StringBuffer values = new StringBuffer();
			int col = 1;
			for(ColumnTO colTo : columns){
				String type = colTo.getType().toUpperCase();
				if(type.indexOf("LOB") == -1 && type.indexOf("LONG") == -1 && type.indexOf("TEXT") == -1 
						&& !isNeedUpdateColumn(tableNm, colTo.getName())){
					String cellNm = convertNum2Col(col,rowLbl);
					colNm.append("IF(").append(cellNm).append("=\"\",\"\",\"").append(colTo.getName()).append(",\")&");
					
					values.append(dbConnect.genValueForInsertSql(type, cellNm));
					
					//for delete and update sql
					if("Y".equalsIgnoreCase(colTo.getIsPk())){
						if(where.indexOf("WHERE") == -1){
							where.append(" WHERE ");
						}else{
							where.append(" AND ");
						}
						where.append(colTo.getName()).append("=").append(dbConnect.genEqValue(type, cellNm));
						
					}
				}//end if
				col++;
			}//end columns
			
			if(where.length() > 1){
				delSql.append(where).append(";\"");
				XSSFCell delCell = sheet.getRow(rowIndex).createCell(delSqlColIndex);
				delCell.setCellFormula(delSql.toString());
				
				//generate update sql 
				boolean needGenUpdateSql = updateFkColMap.containsKey(tableNm);
				if(needGenUpdateSql){
					StringBuffer updateAfterInsertSql = new StringBuffer(); //used for update circle FK after insert
					StringBuffer updateBeforeDelete = new StringBuffer(); //used for update circle FK before delete
					StringBuffer ifNullSql = new StringBuffer();
					StringBuffer setSqlForInsert = new StringBuffer();
					StringBuffer setSqlForDelete = new StringBuffer();
					String[] fkCols = updateFkColMap.get(tableNm).split(Constants.SEPARATE);
					
					for(int n = 0 ; n < fkCols.length; n++){
						String fkCol = fkCols[n].trim();
						for(int i = 0 ; i < columns.size() ; i++){
							if(fkCol.equalsIgnoreCase(columns.get(i).getName())){
								String cellNm = convertNum2Col(i + 1, rowLbl);
								if(n > 0){
									ifNullSql.append(",");
									setSqlForInsert.append("&");
									setSqlForDelete.append(", ");
								}
								ifNullSql.append(cellNm).append("=\"\"");
								setSqlForInsert.append("IF(").append(cellNm).append("=\"\",\"\",\"").append(fkCol).append("=")
								      .append(dbConnect.genEqValue(columns.get(i).getType(), cellNm)).append(", \")");
								
								setSqlForDelete.append(fkCol).append(" = NULL ");
							}
						}
					}
					
					updateAfterInsertSql.append("IF(");
					updateBeforeDelete.append("IF(");
					if(fkCols.length > 1){
						updateAfterInsertSql.append("AND(");
						updateBeforeDelete.append("AND(");
					}
					updateAfterInsertSql.append(ifNullSql);
					updateBeforeDelete.append(ifNullSql);
					if(fkCols.length > 1){
						updateAfterInsertSql.append(")");
						updateBeforeDelete.append(")");
					}
					
					int updateSetCellInd = delSqlColIndex + 3;
					XSSFCell updateSetCell = sheet.getRow(rowIndex).createCell(updateSetCellInd);
					updateSetCell.setCellFormula(setSqlForInsert.toString());
					String cellNm = convertNum2Col(updateSetCellInd, rowLbl);
					
					updateAfterInsertSql.append(", \"\", \"UPDATE ").append(tableNm)
			                 .append(" SET \"&LEFT(").append(cellNm).append(",LEN(").append(cellNm).append(")-2)&\" ") // -2: remove the last ", " in the setsql part
			                 .append(where).append(";\")");
					XSSFCell updateCell = sheet.getRow(rowIndex).createCell(delSqlColIndex + 2);
					updateCell.setCellFormula(updateAfterInsertSql.toString());
					
					updateBeforeDelete.append(", \"\", \"UPDATE ").append(tableNm)
							.append(" SET ").append(setSqlForDelete).append(where).append(";\")");
					updateCell = sheet.getRow(rowIndex).createCell(delSqlColIndex + 1);
					updateCell.setCellFormula(updateBeforeDelete.toString());
					
					if(!updateBeforeDeleteMap.containsKey(tableNm)){
						updateBeforeDeleteMap.put(tableNm, "UPDATE " + tableNm + " SET " + setSqlForDelete + ";");
					}
					
				}
			}
			log.debug("Generate INSERT sql formula in row " + rowIndex + ".");
			generateInsertSql(sheet,colNm.deleteCharAt(colNm.length()-1).toString(),
					values.deleteCharAt(values.length()-1).toString(), delSqlColIndex + 4, rowIndex);
		}
	}
	
	private boolean isNeedUpdateColumn(String table, String colNm){
		if(updateFkColMap.containsKey(table)){
			for(String fk : updateFkColMap.get(table).split(Constants.SEPARATE)){
				if(colNm.equals(fk)){
					return true;
				}
			}
		}
		return false;
	}
		
	
	//the length of character in formula should be less than 256. If there are many columns in table,
	//it's impossible to write the insert sql in one formula
	private void generateInsertSql(XSSFSheet sheet,String columns,String values,int insertSqlColIndex,int rowNm){		
		String tableNm = sheet.getSheetName();
		/*  insertSqlColIndex     insertSqlColIndex+1      insertSqlColIndex+2         
		 *  final sql             column names             values of columns
		 */
		int rowLbl = rowNm + 1;
		XSSFRow row = sheet.getRow(rowNm);
		if(row == null){
			row = sheet.createRow(3);
		}
		String colString1 = "\"INSERT INTO "+ tableNm + " (\"";
		String colString2 = "\") VALUES (\"";
		
		List<String> colPos = new ArrayList<String>();
		int i = insertSqlColIndex + 3;
		
		StringBuffer sb = new StringBuffer();
		//begin get insertSqlColIndex+1
		String[] arr = columns.split("&");
		for(String s : arr){
			sb.append(s).append("&");
			if(sb.length() > 200){
				sb.deleteCharAt(sb.length()-1);
				XSSFCell f1 = row.createCell(i);
				f1.setCellFormula(sb.toString());
				sb = new StringBuffer();
				colPos.add(convertNum2Col(i,rowLbl));
				i++;
			}
		}
		if(sb.length() > 0){
			sb.deleteCharAt(sb.length()-1);
			XSSFCell f1 = row.createCell(i);
			f1.setCellFormula(sb.toString());
			sb = new StringBuffer();
			colPos.add(convertNum2Col(i,rowLbl));
			i++;
		}
		
		XSSFCell nmCell = row.createCell(insertSqlColIndex+1);
		StringBuffer fomula = new StringBuffer();
		for(String s : colPos){
			fomula.append(s).append("&");
		}
		fomula.deleteCharAt(fomula.length()-1);
		nmCell.setCellFormula(fomula.toString());
		colPos.clear();
		//end get col+1
		
		//begin get col+2		
		arr = values.split(Constants.SEPARATE);
		for(String s : arr){
			sb.append(s).append("&");
			if(sb.length() > 200){
				sb.deleteCharAt(sb.length()-1);
				XSSFCell f1 = row.createCell(i);
				f1.setCellFormula(sb.toString());
				sb = new StringBuffer();
				colPos.add(convertNum2Col(i,rowLbl));
				i++;
			}
		}
		if(sb.length() > 0){
			sb.deleteCharAt(sb.length()-1);
			XSSFCell f1 = row.createCell(i);
			f1.setCellFormula(sb.toString());
			sb = new StringBuffer();
			colPos.add(convertNum2Col(i,rowLbl));
			i++;
		}
		
		XSSFCell varCell = row.createCell(insertSqlColIndex+2);
		fomula = new StringBuffer();
		for(String s : colPos){
			fomula.append(s).append("&");
		}
		fomula.deleteCharAt(fomula.length()-1);
		varCell.setCellFormula(fomula.toString());
		//end get col+2		
		
		//set final fomula
		XSSFCell fomulaCell = row.createCell(insertSqlColIndex);
		fomula = new StringBuffer();
		String nameCol = convertNum2Col(insertSqlColIndex+1,rowLbl);
		String varCol = convertNum2Col(insertSqlColIndex+2,rowLbl);
		fomula.append(colString1).append("&LEFT(")
		      .append(nameCol).append(",LEN(").append(nameCol).append(")-1)") // -1: Remove the last ","
		      .append("&").append(colString2)
		      .append("&LEFT(").append(varCol).append(",LEN(").append(varCol).append(")-1)")
		      .append("&\");\"");
		fomulaCell.setCellFormula(fomula.toString());
		
	}
	
	/*   col      char     ascii
	 *    1        B       66
	 *    2        C       67
	 *    ...
	 *    25       Z       90
	 *    26       AA      65+65
	 *    27       AB      65+66
	 *    ...
	 *    51       AZ      65+90
	 *    52       BA      66+65
	 *    
	 *    if col = 1 and rowLbl = 5, return "C5", it means the cell in the excel is "C5"
	 */
	private String convertNum2Col(int col,int rowLbl){
		StringBuffer str = new StringBuffer();
		char ch;		
		int shan = col/26;
		int yushu = col%26;
		
		if(shan != 0){
			ch = (char)(shan+64);
			str.append(ch);
		}
		ch = (char)(yushu+65);
		str.append(ch);
		str.append(rowLbl+"");
		return str.toString();
	}
	
	private void initFontAndStyle(XSSFWorkbook book){
		headFont = book.createFont();
		headFont.setFontHeightInPoints((short)9);
		headFont.setFontName("Arial");
		
		contentCs = book.createCellStyle();
		contentCs.setFont(headFont);
		contentCs.setBorderBottom(XSSFCellStyle.BORDER_THIN);
		contentCs.setBorderLeft(XSSFCellStyle.BORDER_THIN);
		contentCs.setBorderRight(XSSFCellStyle.BORDER_THIN);
		contentCs.setBorderTop(XSSFCellStyle.BORDER_THIN);
		contentCs.setAlignment(XSSFCellStyle.ALIGN_CENTER);
		contentCs.setDataFormat((short)0x31); //set the cell as string type	
		
		headStyle = book.createCellStyle();
		headStyle.setFont(headFont);
		headStyle.setBorderBottom(XSSFCellStyle.BORDER_THIN);
		headStyle.setBorderLeft(XSSFCellStyle.BORDER_THIN);
		headStyle.setBorderRight(XSSFCellStyle.BORDER_THIN);
		headStyle.setBorderTop(XSSFCellStyle.BORDER_THIN);
		headStyle.setAlignment(XSSFCellStyle.ALIGN_CENTER);
		headStyle.setFillPattern(XSSFCellStyle.SOLID_FOREGROUND);
		headStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
		
		linkStyle = book.createCellStyle();
		XSSFFont linkFont = book.createFont();
		linkFont.setUnderline(XSSFFont.U_SINGLE);
		linkFont.setColor(IndexedColors.BLUE.getIndex());
		linkFont.setFontHeightInPoints((short)9);
	    linkStyle.setFont(linkFont);
	    linkStyle.setBorderBottom(XSSFCellStyle.BORDER_THIN);
	    linkStyle.setBorderLeft(XSSFCellStyle.BORDER_THIN);
	    linkStyle.setBorderRight(XSSFCellStyle.BORDER_THIN);
	    linkStyle.setBorderTop(XSSFCellStyle.BORDER_THIN);
	    
	    pkcs = book.createCellStyle();
	    pkcs.setFont(headFont);
	    pkcs.setBorderBottom(XSSFCellStyle.BORDER_THIN);
	    pkcs.setBorderLeft(XSSFCellStyle.BORDER_THIN);
	    pkcs.setBorderRight(XSSFCellStyle.BORDER_THIN);
	    pkcs.setBorderTop(XSSFCellStyle.BORDER_THIN);
	    pkcs.setAlignment(XSSFCellStyle.ALIGN_LEFT);
	}
	
}
