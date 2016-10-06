package org.rci.testdataholder;

public interface Constants {

	//public static String FOLDER = "src/test/resources/"; //only maven project has this folder
	
	public static String TABLE_NAME = "TABLE_NAME";
	public static String COLUMN_NAME = "COLUMN_NAME";
	public static String DATA_TYPE = "DATA_TYPE";
	public static String DATA_LENGTH = "DATA_LENGTH";
	public static String NULLABLE = "NULLABLE";
	public static String IS_PK = "IS_PK";
	
	public static String SEPARATE = ";";
	
	// the content of row 1 ~ 3 are "Column Name", "Data Type" and "Mandatory". So the test data is from row4 (which index is 3).
	public static int FIRST_CONTENT_ROW_IND = 3; 
	
	public static String VERION_TABLE_MAP_SHEET_NAME = "Version Table Map";
	
	public static int SORTED_TABLE_COL_IND = 7;
	
	public static int UPDATE_SQL_COL_IND = 20;
	
	public static String INDEX_SHEET_NAME = "INDEX";
	
	public static int PK_COL_IND = 2;
}
