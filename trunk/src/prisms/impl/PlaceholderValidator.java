/*
 * PlaceholderAppConfig.java Created Dec 4, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/**
 * A placeholder for a {@link prisms.arch.Validator} subclass that cannot be identified. It may be
 * meant for configuration on another server. This class throws an error if application validation
 * is attempted.
 */
public class PlaceholderValidator implements prisms.arch.Validator
{
	private final String theValidatorClassName;

	/**
	 * Creates a placeholder for a {@link prisms.arch.Validator} subclass
	 * 
	 * @param className The name of the subclass that cannot be identified
	 */
	public PlaceholderValidator(String className)
	{
		theValidatorClassName = className;
	}

	/**
	 * @return The name of the subclass that this placeholder stands for
	 */
	public String getValidatorClassName()
	{
		return theValidatorClassName;
	}

	public JSONObject getValidationInfo(User user, PrismsApplication app, ClientConfig client,
		HttpServletRequest req)
	{
		throw new IllegalStateException("Unrecognized Validator implementation: "
			+ theValidatorClassName);
	}

	public boolean validate(User user, PrismsApplication app, ClientConfig client,
		HttpServletRequest request, JSONObject data) throws IOException
	{
		throw new IllegalStateException("Unrecognized Validator implementation: "
			+ theValidatorClassName);
	}
}
