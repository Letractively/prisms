--SET ECHO ON
-- -------------------------------------------------------------------------------------------------
--The schema for the Plugin Remote Integrated Service Management System (PRISMS) database.
--This script works with Microsoft SQL Server.  Modifications may be needed for other database flavors.
-- -------------------------------------------------------------------------------------------------
--PRISMS ARCHITECTURE TABLES
-- -------------------------------------------------------------------------------------------------
CREATE TABLE prisms_installation(
	centerID INT NOT NULL,
	installDate DATETIME NOT NULL
);
GO

CREATE TABLE prisms_instance(
	location VARCHAR(256) NOT NULL,
	initTime DATETIME NOT NULL,
	activeTime DATETIME NOT NULL,
	CONSTRAINT prisms_instance_pk PRIMARY KEY(location)
);
GO

CREATE TABLE prisms_auto_increment(
	tableName VARCHAR(32) NOT NULL,
	whereClause VARCHAR(64) NOT NULL,
	nextID NUMERIC(20) NOT NULL,
	CONSTRAINT prisms_auto_inc_pk PRIMARY KEY(tableName, whereClause)
);
GO

CREATE TABLE prisms_increment_sync(
	tableName VARCHAR(32) NOT NULL,
	whereClause VARCHAR(64) NOT NULL,
	syncTime DATETIME NOT NULL,
	CONSTRAINT prisms_inc_sync_pk PRIMARY KEY(tableName, whereClause)
);
GO

CREATE TABLE prisms_application_status (
	application VARCHAR(32) NOT NULL PRIMARY KEY,
	lockMessage VARCHAR(1024) NULL,
	lockScale INT NULL,
	lockProgress INT NULL,
	reloadProperties INT NULL,
	reloadSessions INT NULL,
	lastUpdate DATETIME NOT NULL
);
GO

CREATE TABLE prisms_hashing (
	id INT NOT NULL,
	multiple INT NOT NULL,
	modulus INT NOT NULL,
	CONSTRAINT prisms_hash_pk PRIMARY KEY(id)
);
GO

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
GO

CREATE TABLE prisms_user (
	id NUMERIC(20) NOT NULL,
	userName VARCHAR(64) NOT NULL,
	isAdmin CHAR(1) NOT NULL,
	isReadOnly CHAR(1) NOT NULL,
	isLocked CHAR(1) NOT NULL,
	deleted CHAR(1) NOT NULL,
	CONSTRAINT prisms_user_pk PRIMARY KEY(id)
);
GO

CREATE TABLE prisms_user_password (
	id INT NOT NULL,
	pwdUser NUMERIC(20) NOT NULL,
	pwdData VARCHAR(256) NULL,
	pwdTime NUMERIC(14) NOT NULL,
	pwdExpire NUMERIC(14) NULL,
	CONSTRAINT prisms_pwd_pk PRIMARY KEY(id),	
	CONSTRAINT prisms_user_password_fk FOREIGN KEY(pwdUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);
GO

CREATE TABLE prisms_user_app_assoc (
	assocUser NUMERIC(20) NOT NULL,
	assocApp VARCHAR(32) NOT NULL,
	CONSTRAINT prisms_user_app_pk PRIMARY KEY(assocUser, assocApp),
	CONSTRAINT prisms_user_app_fk FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);
GO

CREATE TABLE prisms_user_group (
	id NUMERIC(20) NOT NULL,
	groupApp VARCHAR(32) NOT NULL,
	groupName VARCHAR(64) NOT NULL,
	groupDescrip VARCHAR(512) NULL,
	deleted CHAR(1) NOT NULL,
	CONSTRAINT prisms_group_pk PRIMARY KEY(id)
);
GO

CREATE TABLE prisms_user_group_assoc (
	assocUser NUMERIC(20) NOT NULL,
	assocGroup NUMERIC(20) NOT NULL,
	CONSTRAINT prisms_user_group_pk PRIMARY KEY(assocUser, assocGroup),
	CONSTRAINT prisms_group_user_fk FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE,
	CONSTRAINT prisms_group_fk FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);
GO

CREATE TABLE prisms_group_permissions (
	assocGroup NUMERIC(20) NOT NULL,
	pApp VARCHAR(32) NOT NULL,
	assocPermission VARCHAR(64) NOT NULL,
	CONSTRAINT prisms_group_perm_pk PRIMARY KEY(assocGroup, pApp, assocPermission),
	CONSTRAINT prisms_perm_group_fk FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);
GO

-- End of PRISMS-proper table schema

-- The PRISMS logging table persists logging information to a more manipulable form

CREATE TABLE prisms_log_entry (
	id INT NOT NULL,
	logInstance VARCHAR(256) NOT NULL,
	logTime DATETIME NOT NULL,
	logApp VARCHAR(64) NULL,
	logClient VARCHAR(64) NULL,
	logUser NUMERIC(20) NULL,
	logSession VARCHAR(16) NULL,
	trackingData VARCHAR(256) NULL,
	logLevel INT NOT NULL,
	loggerName VARCHAR(128) NOT NULL,
	shortMessage VARCHAR(100) NULL,
	messageCRC NUMERIC(14) NOT NULL,
	stackTraceCRC NUMERIC(14) NOT NULL,
	logDuplicate INT NULL,
	entrySize INT NOT NULL,
	entrySaved DATETIME NULL,

	CONSTRAINT prisms_log_pk PRIMARY KEY(id),
	CONSTRAINT prisms_log_duplicate_fk FOREIGN KEY(logDuplicate) REFERENCES prisms_log_entry(id)
);
GO

CREATE TABLE prisms_log_content (
	logEntry INT NOT NULL,
	indexNum INT NOT NULL,
	content VARCHAR(1024) NOT NULL,
	isStackTrace CHAR(1) NOT NULL,

	CONSTRAINT prisms_log_msg_fk FOREIGN KEY(logEntry) REFERENCES prisms_log_entry(id) ON DELETE CASCADE
);

CREATE TABLE prisms_log_auto_purge (
	setTime DATETIME NOT NULL,
	maxSize INT NOT NULL,
	maxAge NUMERIC(14) NOT NULL
);
GO

CREATE TABLE prisms_log_purge_exclude (
	search VARCHAR(1024) NOT NULL
);
GO

CREATE TABLE prisms_logger_config (
	logger VARCHAR(128) NOT NULL,
	logLevel INT NULL,
	setTime DATETIME NOT NULL,
	CONSTRAINT prisms_logger_config_pk PRIMARY KEY(logger)
);
GO

-- The PRISMS preference table allows applications to store small user-specific data persistently
CREATE TABLE prisms_preference (
	id NUMERIC(20) NOT NULL,
	pApp VARCHAR(64) NOT NULL,
	pUser VARCHAR(64) NOT NULL,
	pDomain VARCHAR(64) NOT NULL,
	pName VARCHAR(256) NOT NULL,
	pType VARCHAR(256) NOT NULL,
	pDisplayed CHAR(1) NOT NULL,
	pValue VARCHAR(1024) NULL,
	CONSTRAINT prisms_pref_pk PRIMARY KEY(id),
	CONSTRAINT prisms_pref_unq UNIQUE(pApp, pUser, pDomain, pName)
);
GO

-- The PRISMS records schema allows applications to keep track of changes to sets of data, keep that
-- data consistent between instances on a given enterprise, and to synchronize data sets between
-- servers.

CREATE TABLE prisms_change_record(
	id NUMERIC(20) NOT NULL,
	recordNS VARCHAR(32) NOT NULL,
	localOnly CHAR(1) NOT NULL,
	changeTime DATETIME NOT NULL,
	changeUser NUMERIC(20) NOT NULL,
	subjectType VARCHAR(32) NOT NULL,
	changeType VARCHAR(32) NULL,
	additivity CHAR(1) NOT NULL,
	subjectCenter INT NOT NULL,
	majorSubject NUMERIC(20) NOT NULL,
	minorSubject NUMERIC(20) NULL,
	preValueID NUMERIC(20) NULL,
	shortPreValue VARCHAR(100) NULL,
    longPreValue VARCHAR(MAX) NULL,
	changeData1 NUMERIC(20) NULL,
	changeData2 NUMERIC(20) NULL,
	CONSTRAINT prisms_change_record_pk PRIMARY KEY(recordNS, id)
);
GO

CREATE TABLE prisms_center(
	id INT NOT NULL,
	PRIMARY KEY( id )	
);
GO

CREATE TABLE prisms_center_view(
	id INT NOT NULL,
	recordNS VARCHAR(32) NOT NULL,
	centerID INT NULL,
	name VARCHAR(64) NOT NULL,
	url VARCHAR(512) NULL,
	serverCerts VARBINARY(MAX) NULL,
	serverUserName VARCHAR(64) NULL,
	serverPassword VARCHAR(64) NULL,
	syncFrequency NUMERIC(14) NULL,
	syncPriority INT NOT NULL,
	clientUser NUMERIC(20) NULL,
	changeSaveTime NUMERIC(14) NULL,
	lastImportSync DATETIME NULL,
	lastExportSync DATETIME NULL,
	deleted CHAR(1) NOT NULL,
	CONSTRAINT prisms_center_view_pk PRIMARY KEY(recordNS, id),
	CONSTRAINT prisms_view_center_fk FOREIGN KEY(centerID) REFERENCES prisms_center(id) ON DELETE CASCADE
);
GO

CREATE TABLE prisms_sync_record(
	id INT NOT NULL,
	recordNS VARCHAR(32) NOT NULL,
	syncCenter INT NOT NULL,
	parallelID INT NULL,
	syncTime DATETIME NOT NULL,
	syncType VARCHAR(32) NOT NULL,
	isImport CHAR(1) NOT NULL,
	syncError VARCHAR(1024) NULL,
	CONSTRAINT prisms_sync_record_pk PRIMARY KEY(recordNS, id),
	CONSTRAINT prisms_sync_center_fk FOREIGN KEY(recordNS, syncCenter) REFERENCES prisms_center_view ON DELETE CASCADE
);
GO

CREATE TABLE prisms_sync_assoc(
	recordNS VARCHAR(32),
	syncRecord INT NOT NULL,
	changeRecord NUMERIC(20) NOT NULL,
	error CHAR(1) NOT NULL,
	CONSTRAINT prisms_assoc_sync_fk FOREIGN KEY(recordNS, syncRecord) REFERENCES prisms_sync_record(recordNS, id) ON DELETE CASCADE,
	CONSTRAINT prisms_assoc_change_fk FOREIGN KEY(recordNS, changeRecord) REFERENCES prisms_change_record(recordNS, id) ON DELETE CASCADE
);
GO

CREATE TABLE prisms_auto_purge(
	recordNS VARCHAR(32) NOT NULL,
	entryCount INT NULL,
	age NUMERIC(14) NULL
);
GO

CREATE TABLE prisms_purge_excl_user(
	recordNS VARCHAR(32) NOT NULL,
	exclUser NUMERIC(20) NOT NULL
);
GO

CREATE TABLE prisms_purge_excl_type(
	recordNS VARCHAR(32) NOT NULL,
	exclSubjectType VARCHAR(32) NOT NULL,
	exclChangeType VARCHAR(32) NULL,
	exclAdditivity CHAR(1) NOT NULL
);
GO

CREATE TABLE prisms_purge_record(
	recordNS VARCHAR(32) NOT NULL,
	centerID INT NOT NULL,
	subjectCenter INT NOT NULL,
	latestChange DATETIME NOT NULL
);
GO
