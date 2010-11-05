
----------------------------------------------------------------------------------------------------
--The schema for the Plugin Remote Integrated Service Management System (PRISMS) database.
--This script works with HSQL.  Modifications may be needed for other database flavors.
----------------------------------------------------------------------------------------------------
--PRISMS ARCHITECTURE TABLES
----------------------------------------------------------------------------------------------------

CREATE TABLE prisms_installation(
	centerID INT NOT NULL,
	installDate TIMESTAMP NOT NULL
);

CREATE TABLE prisms_auto_increment(
	tableName VARCHAR(32) NOT NULL,
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
	userName VARCHAR NOT NULL,
	deleted CHAR(1) NOT NULL
);

CREATE TABLE prisms_user_password (
	id INT NOT NULL PRIMARY KEY,
	pwdUser NUMERIC(20) NOT NULL,
	pwdData VARCHAR NULL,
	pwdTime NUMERIC(14) NOT NULL,
	pwdExpire NUMERIC(14) NULL,

	FOREIGN KEY(pwdUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_app_assoc (
	assocUser NUMERIC(20) NOT NULL,
	assocApp VARCHAR(32) NOT NULL,
	PRIMARY KEY(assocUser, assocApp),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_group (
	id INT NOT NULL PRIMARY KEY,
	groupApp VARCHAR(32) NOT NULL,
	groupName VARCHAR NOT NULL,
	groupDescrip VARCHAR NULL,
	deleted CHAR(1) NOT NULL,
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
	pApp VARCHAR(32) NOT NULL,
	assocPermission VARCHAR(64) NOT NULL,
	PRIMARY KEY(assocGroup, pApp, assocPermission),
	FOREIGN KEY(assocGroup) REFERENCES prisms_user_group(id) ON DELETE CASCADE
);

-- End of PRISMS-proper table schema

-- The PRISMS preference table allows applications to store small user-specific data persistently
CREATE TABLE prisms_preference (
	pApp VARCHAR NOT NULL,
	pUser VARCHAR NOT NULL,
	pDomain VARCHAR NOT NULL,
	pName VARCHAR NOT NULL,
	pType VARCHAR NOT NULL,
	pDisplayed CHAR(1) NOT NULL,
	pValue VARCHAR NULL,
	UNIQUE(pApp, pUser, pDomain, pName)
);

-- The PRISMS records schema allows applications to keep track of changes to sets of data and to
-- synchronize data sets between servers

CREATE TABLE prisms_change_record(
	id NUMERIC(20) NOT NULL,
	recordNS VARCHAR(32) NOT NULL,
	changeTime TIMESTAMP NOT NULL,
	changeUser NUMERIC(20) NOT NULL,
	subjectType VARCHAR(32) NOT NULL,
	changeType VARCHAR(32) NULL,
	additivity CHAR(1) NOT NULL,
	subjectCenter INT NOT NULL,
	majorSubject NUMERIC(20) NOT NULL,
	minorSubject NUMERIC(20) NULL,
	preValueID NUMERIC(20) NULL,
	shortPreValue VARCHAR(100) NULL,
    longPreValue LONGVARCHAR NULL,    
	changeData1 NUMERIC(20) NULL,
	changeData2 NUMERIC(20) NULL,

	PRIMARY KEY(id, recordNS)
);

CREATE TABLE prisms_center(
	id INT NOT NULL PRIMARY KEY
);

CREATE TABLE prisms_center_view(
	id INT NOT NULL PRIMARY KEY,
	recordNS VARCHAR(32) NOT NULL,
	centerID INT NULL,
	name VARCHAR(64) NOT NULL,
	url VARCHAR(512) NULL,
	serverUserName VARCHAR(64) NULL,
	serverPassword VARCHAR(64) NULL,
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
	recordNS VARCHAR(32) NOT NULL,
	syncCenter INT NOT NULL,
	parallelID INT NULL,
	syncTime TIMESTAMP NOT NULL,
	syncType VARCHAR(32) NOT NULL,
	isImport CHAR(1) NOT NULL,
	syncError VARCHAR(1024) NULL,

	FOREIGN KEY(syncCenter) REFERENCES prisms_center_view(id) ON DELETE CASCADE
);

CREATE TABLE prisms_sync_assoc(
	recordNS VARCHAR(32),
	syncRecord INT NOT NULL,
	changeRecord NUMERIC(20) NOT NULL,
	error CHAR(1) NOT NULL,

	FOREIGN KEY(syncRecord) REFERENCES prisms_sync_record(id) ON DELETE CASCADE,
	FOREIGN KEY(changeRecord, recordNS) REFERENCES prisms_change_record(id, recordNS) ON DELETE CASCADE
);

CREATE TABLE prisms_auto_purge(
	recordNS VARCHAR(32) NOT NULL,
	entryCount INT NULL,
	age NUMERIC(14) NULL
);

CREATE TABLE prisms_purge_excl_user(
	recordNS VARCHAR(32) NOT NULL,
	exclUser NUMERIC(20) NOT NULL
);

CREATE TABLE prisms_purge_excl_type(
	recordNS VARCHAR(32) NOT NULL,
	exclSubjectType VARCHAR(32) NOT NULL,
	exclChangeType VARCHAR(32) NULL,
	exclAdditivity CHAR(1) NOT NULL
);

CREATE TABLE prisms_purge_record(
	recordNS VARCHAR(32) NOT NULL,
	centerID INT NOT NULL,
	subjectCenter INT NOT NULL,
	latestChange TIMESTAMP NOT NULL
);

--The PRISMS messaging schema allows email-like messages to be sent to other users within the same
--application

CREATE TABLE prisms_message(
	id INT NOT NULL PRIMARY KEY,
	author INT NOT NULL,
	time TIMESTAMP NOT NULL,
	sent CHAR(1) NOT NULL,
	priority INT NOT NULL,
	predecessor INT NULL,
	override CHAR(1) NOT NULL,
	subject VARCHAR2 NOT NULL,
	content LONGVARCHAR NOT NULL,

	FOREIGN KEY(author) REFERENCES prisms_user(id) ON DELETE CASCADE,
	FOREIGN KEY(predecessor) REFERENCES prisms_message(id)
);

CREATE TABLE prisms_message_recipient(
	message INT NOT NULL,
	recipient INT NOT NULL,
	applicability INT NOT NULL,
	readTime TIMESTAMP NULL,

	FOREIGN KEY(message) REFERENCES prisms_message(ID) ON DELETE CASCADE,
	FOREIGN KEY(recipient) REFERENCE prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_message_attachment(
	id INT NOT NULL PRIMARY KEY,
	message INT NOT NULL,
	name VARCHAR NOT NULL,
	type VARCHAR NOT NULL,
	data LONGVARBINARY NOT NULL,

	FOREIGN KEY(message) REFERENCES prisms_message(id) ON DELETE CASCADE
);
