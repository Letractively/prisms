/*
 * PrismsSubjectType.java Created Dec 3, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.records.ChangeType;

/** All {@link prisms.records.SubjectType}s available for PRISMS data */
public enum PrismsSubjectType implements prisms.records.SubjectType
{
	/** Changes to the set of users */
	user(prisms.arch.ds.User.class, null, null, PrismsChangeTypes.UserChange.class),
	/** Changes to the set of groups */
	group(prisms.arch.ds.UserGroup.class, prisms.arch.PrismsApplication.class, null,
		PrismsChangeTypes.GroupChange.class);

	private final Class<?> theMajorType;

	private final Class<?> theMDType1;

	private final Class<?> theMDType2;

	private final Class<? extends Enum<? extends ChangeType>> theChangeClass;

	PrismsSubjectType(Class<?> majorType, Class<?> md1, Class<?> md2,
		Class<? extends Enum<? extends ChangeType>> fieldsClass)
	{
		theMajorType = majorType;
		theMDType1 = md1;
		theMDType2 = md2;
		theChangeClass = fieldsClass;
	}

	public Class<?> getMajorType()
	{
		return theMajorType;
	}

	public Class<?> getMetadataType1()
	{
		return theMDType1;
	}

	public Class<?> getMetadataType2()
	{
		return theMDType2;
	}

	public Class<? extends Enum<? extends ChangeType>> getChangeTypes()
	{
		return theChangeClass;
	}
}
