DROP TABLE TBL_SHJ_STAFF CASCADE CONSTRAINTS;
DROP TABLE  TBL_SHJ_DEPARTMENT CASCADE CONSTRAINTS;
DROP TABLE  TBL_SHJ_COMPANY CASCADE CONSTRAINTS;

CREATE TABLE "TBL_SHJ_COMPANY" 
(	"ID" VARCHAR2(32 BYTE) NOT NULL, 
	"ADDRESS" VARCHAR2(255 BYTE), 
	"CREATED_BY" VARCHAR2(50 BYTE), 
	"CREATED_DT" DATE, 
	"ESTABLISH_DATE" DATE, 
	"COM_NAME" VARCHAR2(100 BYTE) NOT NULL, 
	"POSTCODE" NUMBER(10,0), 
	"UPDATED_BY" VARCHAR2(50 BYTE), 
	"UPDATED_DT" DATE, 
	"VERSION" NUMBER(10,0) NOT NULL, 
	"BOSS_ID" VARCHAR2(32 BYTE), 
	 PRIMARY KEY ("ID")
);
   
CREATE TABLE "TBL_SHJ_DEPARTMENT" 
(	"ID" VARCHAR2(32 BYTE) NOT NULL , 
	"CREATED_BY" VARCHAR2(50 BYTE), 
	"CREATED_DT" DATE, 
	"DOMAIN" VARCHAR2(255 BYTE), 
	"DEP_NAME" VARCHAR2(100 BYTE), 
	"UPDATED_BY" VARCHAR2(50 BYTE), 
	"UPDATED_DT" DATE, 
	"VERSION" NUMBER(10,0) NOT NULL , 
	"COMPANY_ID" VARCHAR2(32 BYTE) NOT NULL , 
	"PM_ID" VARCHAR2(32 BYTE), 
	 PRIMARY KEY ("ID"),
	 CONSTRAINT "FK_COMPANY_ID" FOREIGN KEY ("COMPANY_ID")
	  REFERENCES "TBL_SHJ_COMPANY" ("ID") 
) ;


CREATE TABLE "TBL_SHJ_STAFF" 
(	"ID" VARCHAR2(32 BYTE) NOT NULL , 
	"AGE" NUMBER(10,0), 
	"CREATED_BY" VARCHAR2(50 BYTE), 
	"CREATED_DT" DATE, 
	"NAME" VARCHAR2(100 BYTE), 
	"UPDATED_BY" VARCHAR2(50 BYTE), 
	"UPDATED_DT" DATE, 
	"VERSION" NUMBER(10,0) NOT NULL , 
	"DEP_ID" VARCHAR2(32 BYTE) NOT NULL , 
	 PRIMARY KEY ("ID"), 
	 CONSTRAINT "FK_DEP_ID" FOREIGN KEY ("DEP_ID")
	  REFERENCES "TBL_SHJ_DEPARTMENT" ("ID")
);

ALTER TABLE "TBL_SHJ_DEPARTMENT" ADD CONSTRAINT "FK_PM_ID" FOREIGN KEY ("PM_ID")
	  REFERENCES "TBL_SHJ_STAFF" ("ID") ;

ALTER TABLE "TBL_SHJ_COMPANY" ADD CONSTRAINT "FK_BOSS_ID" FOREIGN KEY ("BOSS_ID")
	 REFERENCES "TBL_SHJ_STAFF" ("ID");
 