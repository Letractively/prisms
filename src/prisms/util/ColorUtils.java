/*
 * ColorUtils.java Created Nov 17, 2010 by Andrew Butler, PSL
 */
package prisms.util;

import java.awt.Color;

/** A set of tools for analyzing and manipulating colors */
public class ColorUtils
{
	private ColorUtils()
	{
	}

	/**
	 * Serializes a color to its HTML markup (e.g. "#ff0000" for red)
	 * 
	 * @param c The color to serialize
	 * @return The HTML markup of the color
	 */
	public static String toHTML(Color c)
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
	public static Color fromHTML(String htmlColor)
	{
		int r, g, b;
		if(htmlColor.length() != 7 || htmlColor.charAt(0) != '#')
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		r = Integer.parseInt(htmlColor.substring(1, 3), 16);
		g = Integer.parseInt(htmlColor.substring(3, 5), 16);
		b = Integer.parseInt(htmlColor.substring(5, 7), 16);
		return new Color(r, g, b);
	}

	/**
	 * Performs a somewhat subjective analysis of a color to determine how dark it looks to a user
	 * 
	 * @param color The color to analyze
	 * @return The darkness of the color
	 */
	public static float getDarkness(Color color)
	{
		float ret = color.getRed() + color.getGreen() + color.getBlue() / 10;
		ret /= (255 + 255 + 255 / 10);
		ret = 1 - ret;
		final float lightDarkBorder = 0.7f;
		if(ret > lightDarkBorder)
			ret = 0.5f + (ret - lightDarkBorder) * 0.5f / (1 - lightDarkBorder);
		else
			ret = ret * 0.5f / lightDarkBorder;
		return ret;
	}

	/**
	 * Lightens a color by a given amount
	 * 
	 * @param color The color to lighten
	 * @param amount The amount to lighten the color. 0 will leave the color unchanged; 1 will make
	 *        the color completely white
	 * @return The bleached color
	 */
	public static Color bleach(Color color, float amount)
	{
		int red = (int) ((color.getRed() * (1 - amount) / 255 + amount) * 255);
		int green = (int) ((color.getGreen() * (1 - amount) / 255 + amount) * 255);
		int blue = (int) ((color.getBlue() * (1 - amount) / 255 + amount) * 255);
		return new Color(red, green, blue);
	}

	/**
	 * Darkens a color by a given amount
	 * 
	 * @param color The color to darken
	 * @param amount The amount to darken the color. 0 will leave the color unchanged; 1 will make
	 *        the color completely black
	 * @return The stained color
	 */
	public static Color stain(Color color, float amount)
	{
		int red = (int) ((color.getRed() * (1 - amount) / 255) * 255);
		int green = (int) ((color.getGreen() * (1 - amount) / 255) * 255);
		int blue = (int) ((color.getBlue() * (1 - amount) / 255) * 255);
		return new Color(red, green, blue);
	}
}
