
--------------------------------------------------------------------------------
--The schema for the Plugin Remote Integrated Service Management System (PRISMS)
--database.  This script works with HSQL.  Modifications may be needed for other
--database flavors.
--------------------------------------------------------------------------------
--PRISMS ARCHITECTURE TABLES
--------------------------------------------------------------------------------

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
	userName VARCHAR NOT NULL,
	UNIQUE(userName)
);

CREATE INDEX prisms_user_by_name ON prisms_user (userName);

CREATE TABLE prisms_user_password (
	id INT NOT NULL PRIMARY KEY,
	pwdUser INT NOT NULL,
	pwdData VARCHAR NULL,
	pwdTime NUMERIC(14) NOT NULL,
	pwdExpire NUMERIC(14) NULL,

	FOREIGN KEY(pwdUser) REFERENCES prisms_user(id) ON DELETE CASCADE
);

CREATE TABLE prisms_application (
	id INT NOT NULL PRIMARY KEY,
	appName VARCHAR NOT NULL,
	appDescrip VARCHAR NULL,
	configClass VARCHAR NOT NULL,
	configXML VARCHAR NOT NULL,
	UNIQUE(appName)
);

CREATE INDEX prisms_application_by_name ON prisms_application (appName);

CREATE TABLE prisms_user_app_assoc (
	assocUser INT NOT NULL,
	assocApp INT NOT NULL,
	encryption CHAR(1) NOT NULL,
	validationClass VARCHAR NULL,
	PRIMARY KEY(assocUser, assocApp),
	FOREIGN KEY(assocUser) REFERENCES prisms_user(id) ON DELETE CASCADE,
	FOREIGN KEY(assocApp) REFERENCES prisms_application(id) ON DELETE CASCADE
);

CREATE TABLE prisms_client_config (
	id INT NOT NULL PRIMARY KEY,
	configApp INT NOT NULL,
	configName VARCHAR NOT NULL,
	configDescrip VARCHAR NULL,
	configSerializer VARCHAR NULL,
	configXML VARCHAR NOT NULL,
	sessionTimeout NUMERIC(14) NOT NULL,
	UNIQUE(configApp, configName),
	FOREIGN KEY(configApp) REFERENCES prisms_application(id) ON DELETE CASCADE
);

CREATE TABLE prisms_user_group (
	id INT NOT NULL PRIMARY KEY,
	groupName VARCHAR NOT NULL,
	groupDescrip VARCHAR NULL,
	groupApp INT NOT NULL,
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
	pName VARCHAR NOT NULL,
	pDescrip VARCHAR NULL,
	UNIQUE(pApp, pName),
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
	pApp VARCHAR NOT NULL,
	pUser VARCHAR NOT NULL,
	pDomain VARCHAR NOT NULL,
	pName VARCHAR NOT NULL,
	pType VARCHAR NOT NULL,
	pDisplayed CHAR(1) NOT NULL,
	pValue VARCHAR NULL,
	UNIQUE(pApp, pUser, pDomain, pName)
);
