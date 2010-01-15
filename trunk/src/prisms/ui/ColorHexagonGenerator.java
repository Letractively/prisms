/*
 * ColorHexagonGenerator.java Created Jan 14, 2010 by Andrew Butler, PSL
 */
package prisms.ui;

/**
 * Generates a
 */
public class ColorHexagonGenerator
{
	private static class ColorMetadata
	{
		int height;

		int width;

		float sideLength;

		float sqrt3;

		int background;

		ColorMetadata(int _dim)
		{
			height = _dim;
			width = (int) Math.ceil(_dim * Math.sqrt(3) / 2);
			sideLength = height / 2.0f;
			sqrt3 = (float) Math.sqrt(3);
			background = 0x00000000;
		}

		int getRGB(int x, int y)
		{
			x -= width / 2;
			y -= height / 2;
			y = -y;

			float r, g, b;
			if(x >= 0)
			{
				if(y >= x / sqrt3)
				{
					r = 1;
					g = 1 - (y - x / sqrt3) / sideLength;
					b = 1 - (y + x / sqrt3) / sideLength;
				}
				else
				{
					r = 1 - (x / sqrt3 - y) / sideLength;
					g = 1;
					b = 1 - 2 * x / sqrt3 / sideLength;
				}
			}
			else
			{
				if(y >= -x / sqrt3)
				{
					r = 1;
					g = 1 - (y - x / sqrt3) / sideLength;
					b = 1 - (y + x / sqrt3) / sideLength;
				}
				else
				{
					r = 1 + (y + x / sqrt3) / sideLength;
					g = 1 + 2 * x / sqrt3 / sideLength;
					b = 1;
				}
			}

			if(r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1)
				return background;
			int ret = 0xFF000000;
			ret |= Math.round(r * 255) << 16;
			ret |= Math.round(g * 255) << 8;
			ret |= Math.round(b * 255);
			return ret;
		}
	}

	/**
	 * The main method
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		generate(128, "C:\\Documents And Settings\\Andrew\\Desktop\\ColorHexagon.png");
	}

	/**
	 * Generates an RGB color hexagon and writes it to a PNG image file
	 * 
	 * @param dim The largest dimension of the image to generate
	 * @param fileName The name of the file to write to
	 */
	public static void generate(int dim, String fileName)
	{
		ColorMetadata md = new ColorMetadata(dim);
		java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(md.width, dim,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < md.height; y++)
			for(int x = 0; x < md.width; x++)
				im.setRGB(x, y, md.getRGB(x, y));
		try
		{
			java.io.FileOutputStream out = new java.io.FileOutputStream(fileName);
			javax.imageio.ImageIO.write(im, "png", out);
			out.flush();
			out.close();
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not write image file " + fileName, e);
		}
	}
}
