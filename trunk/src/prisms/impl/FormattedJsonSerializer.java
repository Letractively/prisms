/**
 * 
 */
package prisms.impl;

/**
 * @author Andrew Butler
 */
public class FormattedJsonSerializer extends prisms.arch.JsonSerializer {

	@Override
	public String serialize(org.json.simple.JSONArray events)
			throws java.io.NotSerializableException {
		validate(events);
		return prisms.util.JsonUtils.format(events);
	}
}
