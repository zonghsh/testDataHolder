package org.rci.testdataholder.loader;

public interface Loader {
	
	/**
	 * If some other versions (may be prepared by other staff) are also needed for the test 
	 * case, they can be added as the parameter. E.g. The test case needs data version 100 and 200, if the data in version 200 depends on
	 * the data in version 100 (e.g. foreign key constraint), this method will be invoked as loadDataByVersion(200, 100);
	 *                 
	 * @param versions The first version is the main data version. 
	 * @throws Exception
	 */
	public void loadDataByVersion(int... versions) throws Exception;
	
	public void setPreloadDataFile(String filePath);
	
	public void setSpecialProcessFile(String specialProcessFile);
	
	public void setAuditTblPrefix(String auditTblPrefix) ;

	public void setAuditTblSuffix(String auditTblSuffix);
	
	public void initDatabaseData() throws Exception;
	
}
