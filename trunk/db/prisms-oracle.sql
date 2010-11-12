--SET ECHO ON
-- -------------------------------------------------------------------------------------------------
--The schema for the Plugin Remote Integrated Service Management System (PRISMS) database
-- -------------------------------------------------------------------------------------------------
--PRISMS ARCHITECTURE TABLES
-- -------------------------------------------------------------------------------------------------

CREATE TABLE prisms_installation(
	centerID INT NOT NULL,
	installDate TIMESTAMP NOT NULL
);

CREATE TABLE prisms_auto_increment(
	tableName VARCHAR2(32) NOT NULL,
	nextID	  NUMERIC(20) NOT NULL
);

CREATE TABLE prisms_hashing (
	id INT NOT NULL PRIMARY KEY,
	multiple INT NOT NULL,
	modulus INT NOT NULL
);

CREATE TABLE prisms_password_constraints(
	constraintsLocked CHAR(1) NOT NULL,
	minCharLength INT NULL,
	minUpperCase INT NULL,
	minLowerCase INT NULL,
	minDigits INT NULL,
	minSpecialChars INT NULL,
	maxPasswordDuration NUMERIC(14) NULL,
	numPreviousUnique INT NULL,
	minChangeInterval NUMERIC(14) NULL
);

CREATE TABLE prisms_user (
	id NUMERIC(20) NOT NULL PRIMARY KEY,
	userName VARCHAR2(128) NOT NULL,
	isAdmin CHAR(1) NOT NULL,
	isReadOnly CHAR(1) NOT NULL,
	deleted CHAR(1) NOT NULL
);

CREATE TABLE prisms_user_password (
	id INT NOT NULL PRIMARY KEY,
	pwdUser NUMERIC(20) NOT NULL,
	pwdData VARCHAR2(1024) NULL,
	pwdTime NUMERIC(14) NOT NULL,
	pwdExpire NUMERIC(14) NULL,
	FOREIGN KEY(pwdUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_app_assoc (
	assocUser NUMERIC(20) NOT NULL,
	assocApp VARCHAR2(32) NOT NULL,
	PRIMARY KEY(assocUser, assocApp),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_group (
	id INT NOT NULL PRIMARY KEY,
	groupApp VARCHAR2(32) NOT NULL,
	groupName VARCHAR2(64) NOT NULL,
	groupDescrip VARCHAR2(512) NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(groupApp) REFERENCES prisms_application(id) ON DELETE CASCADE,
	UNIQUE(groupApp, groupName)
);

CREATE TABLE prisms_user_group_assoc (
	assocUser NUMERIC(20) NOT NULL,
	assocGroup INT NOT NULL,
	PRIMARY KEY(assocUser, assocGroup),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE,
	FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);

CREATE TABLE prisms_group_permissions (
	assocGroup INT NOT NULL,
	pApp VARCHAR2(32) NOT NULL,
	assocPermission VARCHAR(64) NOT NULL,
	PRIMARY KEY(assocGroup, pApp, assocPermission),
	FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);

CREATE TABLE prisms_preference (
	pApp VARCHAR2(32) NOT NULL,
	pUser VARCHAR2(128) NOT NULL,
	pDomain VARCHAR2(128) NOT NULL,
	pName VARCHAR2(128) NOT NULL,
	pType VARCHAR2(32) NOT NULL,
	pDisplayed CHAR(1) NOT NULL,
	pValue VARCHAR2(1024) NULL,
	UNIQUE (pApp, pUser, pDomain, pName)
);

CREATE TABLE prisms_change_record(
	id NUMERIC(20) NOT NULL,
	recordNS VARCHAR2(32) NOT NULL,
	changeTime TIMESTAMP NOT NULL,
	changeUser NUMERIC(20) NOT NULL,
	subjectType VARCHAR2(32) NOT NULL,
	changeType VARCHAR2(32) NULL,
	additivity CHAR(1) NOT NULL,
	subjectCenter INT NOT NULL,
	majorSubject NUMERIC(20) NOT NULL,
	minorSubject NUMERIC(20) NULL,
	preValueID NUMERIC(20) NULL,
	shortPreValue VARCHAR2(100) NULL,
    longPreValue CLOB NULL,
	changeData1 NUMERIC(20) NULL,
	changeData2 NUMERIC(20) NULL,
	PRIMARY KEY(id, recordNS)
);

CREATE TABLE prisms_center(
	id INT NOT NULL PRIMARY KEY
);

CREATE TABLE prisms_center_view(
	id INT NOT NULL PRIMARY KEY,
	recordNS VARCHAR2(32) NOT NULL,
	centerID INT NULL,
	name VARCHAR2(64) NOT NULL,
	url VARCHAR2(512) NULL,
	serverUserName VARCHAR2(64) NULL,
	serverPassword VARCHAR2(64) NULL,
	syncFrequency NUMERIC(14) NULL,
	syncPriority INT NOT NULL,
	clientUser NUMERIC(20) NULL,
	changeSaveTime NUMERIC(14) NULL,
	lastImportSync TIMESTAMP NULL,
	lastExportSync TIMESTAMP NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(centerID) REFERENCES prisms_center(id) ON DELETE CASCADE
);

CREATE TABLE prisms_sync_record(
	id INT NOT NULL PRIMARY KEY,
	recordNS VARCHAR2(32) NOT NULL,
	syncCenter INT NOT NULL,
	parallelID INT NULL,
	syncTime TIMESTAMP NOT NULL,
	syncType VARCHAR2(32) NOT NULL,
	isImport CHAR(1) NOT NULL,
	syncError VARCHAR2(1024) NULL,
	FOREIGN KEY(syncCenter) REFERENCES prisms_center_view(id) ON DELETE CASCADE
);

CREATE TABLE prisms_sync_assoc(
	recordNS VARCHAR2(32),
	syncRecord INT NOT NULL,
	changeRecord NUMERIC(20) NOT NULL,
	error CHAR(1) NOT NULL,
	FOREIGN KEY(syncRecord) REFERENCES prisms_sync_record(id) ON DELETE CASCADE,
	FOREIGN KEY(changeRecord, recordNS) REFERENCES prisms_change_record(id, recordNS) ON DELETE CASCADE
);

CREATE TABLE prisms_auto_purge(
	recordNS VARCHAR2(32) NOT NULL,
	entryCount INT NULL,
	age NUMERIC(14) NULL
);

CREATE TABLE prisms_purge_excl_user(
	recordNS VARCHAR2(32) NOT NULL,
	exclUser NUMERIC(20) NOT NULL
);

CREATE TABLE prisms_purge_excl_type(
	recordNS VARCHAR2(32) NOT NULL,
	exclSubjectType VARCHAR2(32) NOT NULL,
	exclChangeType VARCHAR2(32) NULL,
	exclAdditivity CHAR(1) NOT NULL
);

CREATE TABLE prisms_purge_record(
	recordNS VARCHAR2(64) NOT NULL,
	centerID INT NOT NULL,
	subjectCenter INT NOT NULL,
	latestChange TIMESTAMP NOT NULL
);
