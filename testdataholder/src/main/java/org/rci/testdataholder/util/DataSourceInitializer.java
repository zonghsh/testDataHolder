/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rci.testdataholder.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

public class DataSourceInitializer {

	private static final Log logger = LogFactory.getLog(DataSourceInitializer.class);

	@Autowired
	@Qualifier("genSchemaDataSource")
	private DataSource dataSource;
	
	private String scriptsPath;
	
	private String scriptsFiles;

	private boolean ignoreFailedDrop = true;

	private boolean initialized = true;
	
	private static String SPLIT = ";";
	
	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public void init(){
		if(initialized || StringUtil.isEmpty(scriptsPath)){
			return;
		}
		
		if(!scriptsPath.endsWith("/")){
			scriptsPath = scriptsPath + "/";
		}
		
		
		File file = null;
		List<File> fileList = new ArrayList<File>();
		
		if(StringUtil.isEmpty(scriptsFiles)){
			file = new File(scriptsPath);	
			getFiles(file,fileList);
			
		}else{
			for(String fileName : scriptsFiles.split(SPLIT)){
				file = new File(scriptsPath + fileName.trim());
				if(file.exists()){
					getFiles(file,fileList);
				}else{
					logger.warn("The file (" + file.getName() + ") doesn't exist.");
				}
				
			}
		}
				
		if(fileList.isEmpty()){
			logger.error("No script file found.");
			return;
		}
		
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		
		List<String> alterSql = new LinkedList<String>();
		File tmpFile = null;
		try{
			for(int i = 0 ; i < fileList.size() ; i++){
				tmpFile = fileList.get(i);
				InputStream ins = new FileInputStream(tmpFile);
				String[] scripts = StringUtils.delimitedListToStringArray(StringUtil.stripComments(IOUtils.readLines(ins)), ";");
				for (int j = 0; j < scripts.length; j++) {
					String script = scripts[j].trim();
					if (StringUtils.hasText(script)) {
						try {
							script = StringUtils.capitalize(script);
							
							if(script.indexOf(" DROP ") != -1 || script.startsWith("CREATE TABLE")){
								jdbcTemplate.execute(script);
							}else{
								//execute ALTER CONSTRAINT after tables are all created
								alterSql.add(script);								
							}
						}
						catch (DataAccessException e) {
							if (ignoreFailedDrop && script.toLowerCase().startsWith("drop")) {
								logger.debug("DROP script failed (ignoring): " + script);
							}else {
								throw e;
							}
						}
					}
				}				
			}
		}catch(FileNotFoundException e){
			//won't happen
		}catch(IOException e){
			throw new BeanInitializationException("Cannot load script from [" + tmpFile.getName() + "]", e);
		}
		
		//execute ALTER CONSTRAINT sql
		for(String script : alterSql){
			jdbcTemplate.execute(script);
		}		
	}
	
	
	private void getFiles(File file,List<File> fileList){
		
		if(file.isDirectory()){
			if(file.getName().indexOf(".svn") == -1){
				File[] files = file.listFiles();
				for(File f : files){
					getFiles(f,fileList);
				}
			}
		}else{
			if(file.getName().indexOf(".sql") != -1)
				fileList.add(file);
		}
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setIgnoreFailedDrop(boolean ignoreFailedDrop) {
		this.ignoreFailedDrop = ignoreFailedDrop;
	}

	public String getScriptsFiles() {
		return scriptsFiles;
	}

	public void setScriptsFiles(String scriptsFiles) {
		this.scriptsFiles = scriptsFiles;
	}

	public String getScriptsPath() {
		return scriptsPath;
	}

	public void setScriptsPath(String scriptsPath) {
		this.scriptsPath = scriptsPath;
	}
}
