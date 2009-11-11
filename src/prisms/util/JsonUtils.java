/**
 * JsonUtils.java Created Oct 16, 2007 by Andrew Butler, PSL
 */
package prisms.util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A utility class to simplify manipulating certain kinds of data in the JSON format
 */
public class JsonUtils
{
	private JsonUtils()
	{
	}

	/**
	 * Serializes a set of node actions
	 * 
	 * @param actions The actions to serialize
	 * @return The serialized actions
	 */
	public static org.json.simple.JSONArray serialize(prisms.ui.list.NodeAction[] actions)
	{
		org.json.simple.JSONArray ret = new org.json.simple.JSONArray();
		for(prisms.ui.list.NodeAction a : actions)
		{
			org.json.simple.JSONObject actionObj = new org.json.simple.JSONObject();
			actionObj.put("text", a.getText());
			actionObj.put("multiple", new Boolean(a.getMultiple()));
			ret.add(actionObj);
		}
		return ret;
	}

	/**
	 * Serializes a color to its HTML markup (e.g. "#ff0000" for red)
	 * 
	 * @param c The color to serialize
	 * @return The HTML markup of the color
	 */
	public static String toHTML(java.awt.Color c)
	{
		String ret = "#";
		String hex;
		hex = Integer.toHexString(c.getRed());
		if(hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getGreen());
		if(hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getBlue());
		if(hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		return ret;
	}

	/**
	 * Parses a java.awt.Color from an HTML color string in the form '#RRGGBB' where RR, GG, and BB
	 * are the red, green, and blue bytes in hexadecimal form
	 * 
	 * @param htmlColor The HTML color string to parse
	 * @return The java.awt.Color represented by the HTML color string
	 */
	public static java.awt.Color fromHTML(String htmlColor)
	{
		int r, g, b;
		if(htmlColor.length() != 7 || htmlColor.charAt(0) != '#')
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		r = Integer.parseInt(htmlColor.substring(1, 3), 16);
		g = Integer.parseInt(htmlColor.substring(3, 5), 16);
		b = Integer.parseInt(htmlColor.substring(5, 7), 16);
		return new java.awt.Color(r, g, b);
	}

	/**
	 * Writes a JSON type object into a more human-readable JSON format
	 * 
	 * @param json The object to format
	 * @return A whitespaced human-readable string representing the object
	 */
	public static String format(Object json)
	{
		StringBuilder ret = new StringBuilder();
		format(json, ret, 0);
		return ret.toString();
	}

	private static void format(Object json, StringBuilder ret, int indent)
	{
		if(json instanceof JSONObject)
			formatObject((JSONObject) json, ret, indent);
		else if(json instanceof JSONArray)
			formatArray((JSONArray) json, ret, indent);
		else if(json instanceof String)
		{
			ret.append('"');
			ret.append(json);
			ret.append('"');
		}
		else
			ret.append(json);
	}

	private static void formatObject(JSONObject json, StringBuilder ret, int indent)
	{
		ret.append('{');
		indent++;
		java.util.Iterator<java.util.Map.Entry<String, Object>> iter = json.entrySet().iterator();
		while(iter.hasNext())
		{
			java.util.Map.Entry<String, Object> entry = iter.next();
			ret.append('\n');
			indent(ret, indent);
			ret.append('"');
			ret.append(entry.getKey());
			ret.append('"');
			ret.append(':');
			ret.append(' ');
			format(entry.getValue(), ret, indent);
			if(iter.hasNext())
				ret.append(',');
		}
		if(json.size() > 0)
		{
			ret.append('\n');
			indent(ret, indent - 1);
		}
		ret.append('}');
	}

	private static void formatArray(JSONArray json, StringBuilder ret, int indent)
	{
		ret.append('[');
		indent++;
		java.util.Iterator<Object> iter = json.iterator();
		while(iter.hasNext())
		{
			ret.append('\n');
			indent(ret, indent);
			format(iter.next(), ret, indent);
			if(iter.hasNext())
				ret.append(',');
		}
		if(json.size() > 0)
		{
			ret.append('\n');
			indent(ret, indent - 1);
		}
		ret.append(']');
	}

	private static void indent(StringBuilder ret, int indent)
	{
		for(int i = 0; i < indent; i++)
			ret.append('\t');
	}

	/**
	 * Runs the {@link #format(Object) on the JSON-parsed contents of a file}. Alternately, all
	 * white space is removed fromt the file if the -noformat option is given as the second argument
	 * 
	 * @param args <ol>
	 *        <li>The path to the file to format</li>
	 *        <li>(optional) -noformat</li> </ol?>
	 * @throws java.io.IOException If the file's contents could not be read or written
	 */
	public static void main(String [] args) throws java.io.IOException
	{
		StringBuilder contents = new StringBuilder();
		{
			java.io.Reader fileReader = new java.io.FileReader(args[0]);
			int read = fileReader.read();
			while(read >= 0)
			{
				contents.append((char) read);
				read = fileReader.read();
			}
			fileReader.close();
		}
		Object json = org.json.simple.JSONValue.parse(contents.toString());
		java.io.FileWriter fileWriter = new java.io.FileWriter(args[0]);

		if(args.length == 2 && args[1].equals("-noformat"))
			fileWriter.write(json.toString());
		else
			fileWriter.write(format(json));
		fileWriter.flush();
		fileWriter.close();
	}
}
