/*
 * FormattedJsonSerializer.java Created Jul 1, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

/** Formats the output of PRISMS responses to a more human-readable JSON format */
public class FormattedJsonSerializer extends prisms.arch.JsonSerializer
{
	@Override
	public String serialize(org.json.simple.JSONArray events)
		throws java.io.NotSerializableException
	{
		validate(events);
		return prisms.util.JsonUtils.format(events);
	}
}
