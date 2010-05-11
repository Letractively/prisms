--SET ECHO ON
-- -------------------------------------------------------------------------------------------------
--The schema for the Plugin Remote Integrated Service Management System (PRISMS) database
-- -------------------------------------------------------------------------------------------------
--PRISMS ARCHITECTURE TABLES
-- -------------------------------------------------------------------------------------------------
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
	id INT NOT NULL PRIMARY KEY,
	userName VARCHAR(128) NOT NULL,
	deleted CHAR(1) NOT NULL
);

CREATE TABLE prisms_user_password (
	id INT NOT NULL PRIMARY KEY,
	pwdUser INT NOT NULL,
	pwdData VARCHAR(1024) NULL,
	pwdTime NUMERIC(14) NOT NULL,
	pwdExpire NUMERIC(14) NULL,
	FOREIGN KEY(pwdUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_application (
	id INT NOT NULL PRIMARY KEY,
	appName VARCHAR(64) NOT NULL,
	appDescrip VARCHAR(1024) NULL,
	configClass VARCHAR(256) NOT NULL,
	configXML VARCHAR(256) NOT NULL,
	userRestrictive CHAR(1) NOT NULL,
	deleted CHAR(1) NOT NULL
);

CREATE TABLE prisms_user_app_assoc (
	assocUser INT NOT NULL,
	assocApp INT NOT NULL,
	PRIMARY KEY(assocUser, assocApp),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE,
	FOREIGN KEY(assocApp) REFERENCES prisms_application(id) ON DELETE CASCADE
);

CREATE TABLE prisms_client_config (
	id INT NOT NULL PRIMARY KEY,
	configApp INT NOT NULL,
	configName VARCHAR(128) NOT NULL,
	configDescrip VARCHAR(1024) NULL,
	configSerializer VARCHAR(256) NULL,
	configXML VARCHAR(256) NOT NULL,
	validatorClass VARCHAR(256) NULL,
	isService CHAR(1) NOT NULL,
	sessionTimeout NUMERIC(14) NOT NULL,
	allowAnonymous CHAR(1) NOT NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(configApp) REFERENCES prisms_application(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_group (
	id INT NOT NULL PRIMARY KEY,
	groupName VARCHAR(64) NOT NULL,
	groupDescrip VARCHAR(512) NULL,
	groupApp INT NOT NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(groupApp) REFERENCES prisms_application(id) ON DELETE CASCADE,
	UNIQUE(groupApp, groupName)
);

CREATE TABLE prisms_app_admin_group (
	adminApp INT NOT NULL,
	adminGroup INT NOT NULL,
	PRIMARY KEY(adminApp, adminGroup),
	FOREIGN KEY(adminApp) REFERENCES prisms_application(id) ON DELETE CASCADE,
	FOREIGN KEY(adminGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_group_assoc (
	assocUser INT NOT NULL,
	assocGroup INT NOT NULL,
	PRIMARY KEY(assocUser, assocGroup),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE,
	FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);

CREATE TABLE prisms_permission (
	id INT NOT NULL PRIMARY KEY,
	pApp INT NOT NULL,
	pName VARCHAR(128) NOT NULL,
	pDescrip VARCHAR(256) NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(pApp) REFERENCES prisms_application(id) ON DELETE CASCADE
);

CREATE TABLE prisms_group_permissions (
	assocGroup INT NOT NULL,
	assocPermission INT NOT NULL,
	PRIMARY KEY(assocGroup, assocPermission),
	FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE,
	FOREIGN KEY(assocPermission) REFERENCES prisms_permission(id) ON DELETE CASCADE
);

CREATE TABLE prisms_preference (
	pApp VARCHAR(64) NOT NULL,
	pUser VARCHAR(128) NOT NULL,
	pDomain VARCHAR(128) NOT NULL,
	pName VARCHAR(128) NOT NULL,
	pType VARCHAR(32) NOT NULL,
	pDisplayed CHAR(1) NOT NULL,
	pValue VARCHAR(1024) NULL,
	UNIQUE (pApp, pUser, pDomain, pName)
);

CREATE TABLE prisms_installation(
	recordNS VARCHAR(64) NOT NULL,
	installDate DATETIME NOT NULL
);

CREATE TABLE prisms_change_record(
	id NUMERIC(20) NOT NULL,
	recordNS VARCHAR(64) NOT NULL,
	changeTime DATETIME NOT NULL,
	changeUser NUMERIC(20) NOT NULL,
	subjectType VARCHAR(32) NOT NULL,
	changeType VARCHAR(32) NULL,
	additivity CHAR(1) NOT NULL,
	majorSubject NUMERIC(20) NOT NULL,
	minorSubject NUMERIC(20) NULL,
	preValueID NUMERIC(20) NULL,
	shortPreValue VARCHAR(100) NULL,
    longPreValue VARCHAR(MAX) NULL,
	changeData1 NUMERIC(20) NULL,
	changeData2 NUMERIC(20) NULL,
	PRIMARY KEY(id, recordNS)
);

CREATE TABLE prisms_center(
	id INT NOT NULL PRIMARY KEY
);

CREATE TABLE prisms_center_view(
	id INT NOT NULL PRIMARY KEY,
	recordNS VARCHAR(64) NOT NULL,
	centerID INT NULL,
	name VARCHAR(64) NOT NULL,
	url VARCHAR(512) NULL,
	serverUserName VARCHAR(64) NULL,
	serverPassword VARCHAR(64) NULL,
	syncFrequency NUMERIC(14) NULL,
	clientUser NUMERIC(20) NULL,
	changeSaveTime NUMERIC(14) NULL,
	lastImportSync DATETIME NULL,
	lastExportSync DATETIME NULL,
	deleted CHAR(1) NOT NULL,
	FOREIGN KEY(centerID) REFERENCES prisms_center(id) ON DELETE CASCADE
);

CREATE TABLE prisms_sync_record(
	id INT NOT NULL PRIMARY KEY,
	recordNS VARCHAR(64) NOT NULL,
	syncCenter INT NOT NULL,
	parallelID INT NULL,
	syncTime DATETIME NOT NULL,
	syncType VARCHAR(32) NOT NULL,
	isImport CHAR(1) NOT NULL,
	syncError VARCHAR(1024) NULL,
	FOREIGN KEY(syncCenter) REFERENCES prisms_center_view(id) ON DELETE CASCADE
);

CREATE TABLE prisms_sync_assoc(
	recordNS VARCHAR(64),
	syncRecord INT NOT NULL,
	changeRecord NUMERIC(20) NOT NULL,
	FOREIGN KEY(syncRecord) REFERENCES prisms_sync_record(id) ON DELETE CASCADE,
	FOREIGN KEY(changeRecord, recordNS) REFERENCES prisms_change_record(id, recordNS) ON DELETE CASCADE
);

CREATE TABLE prisms_auto_purge(
	recordNS VARCHAR(64) NOT NULL,
	entryCount INT NULL,
	age NUMERIC(14) NULL
);

CREATE TABLE prisms_purge_excl_user(
	recordNS VARCHAR(64) NOT NULL,
	exclUser NUMERIC(20) NOT NULL
);

CREATE TABLE prisms_purge_excl_type(
	recordNS VARCHAR(64) NOT NULL,
	exclSubjectType VARCHAR(32) NOT NULL,
	exclChangeType VARCHAR(32) NULL,
	exclAdditivity CHAR(1) NOT NULL
);

CREATE TABLE prisms_auto_increment(
	recordNS VARCHAR(64) NOT NULL,
	tableName VARCHAR(32) NOT NULL,
	nextID	  NUMERIC(20) NOT NULL
);
