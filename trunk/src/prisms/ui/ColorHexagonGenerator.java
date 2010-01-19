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
			x -= width / 2 + 1;
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

		boolean shaded = true;

		int getAlphaHex(int x, int y, int rgb)
		{
			if(shaded)
				return getShadedHex(x, y, rgb);
			else
				return getCheckeredHex(x, y, rgb);
		}

		int mod = 64;

		int getShadedHex(int x, int y, int rgb)
		{
			if(rgb == background)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if(r == 255)
			{
				if(g == 255 || b == 255)
					return 0xFF000000;
				r = mod;
				g = mod - ((g + 1) % mod);
				b = mod - ((b + 1) % mod);
				if(g >= mod / 2)
					g = mod - g;
				g *= 2;
				if(b >= mod / 2)
					b = mod - b;
				b *= 2;
			}
			else if(g == 255)
			{
				if(b == 255)
					return 0xFF000000;
				g = mod;
				r = mod - ((r + 1) % mod);
				b = mod - ((b + 1) % mod);
				if(r >= mod / 2)
					r = mod - r;
				r *= 2;
				if(b >= mod / 2)
					b = mod - b;
				b *= 2;
			}
			else
			{
				b = mod;
				r = mod - ((r + 1) % mod);
				g = mod - ((g + 1) % mod);
				if(r >= mod / 2)
					r = mod - r;
				r *= 2;
				if(g >= mod / 2)
					g = mod - g;
				g *= 2;
			}
			float intens = r * 1.0f * g * b / (float) Math.pow(mod, 3);
			intens = (float) Math.pow(intens, 0.5);
			// intens = ((intens + 0.5f) * (intens + 0.5f) - .25f) / 2.25f;

			int dark = Math.round(intens * 255);
			return 0xFF000000 | (dark << 16) | (dark << 8) | dark;
		}

		int getCheckeredHex(int x, int y, int rgb)
		{
			if(rgb == background)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if(r <= 4 || b <= 4 || g <= 4)
				return 0xFF000000;

			int bwg;
			if(r == 255)
				bwg = ((g / mod) + (b / mod)) % 3;
			else if(g == 255)
				bwg = ((r / mod) + (b / mod) + 1) % 3;
			else
				bwg = ((r / mod) + (g / mod) + 2) % 3;
			if(bwg == 0)
				return 0xFF000000;
			else if(bwg == 1)
				return 0xFFFFFFFF;
			else
				return 0xFF808080;
		}
	}

	/**
	 * The main method
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		generate(128, "C:\\Documents And Settings\\Andrew\\Desktop");
	}

	/**
	 * Generates an RGB color hexagon, a plain black hexagon, and a black-and-white patterned
	 * hexagon and writes them to PNG image files
	 * 
	 * @param dim The largest dimension of the image to generate
	 * @param dirName The name of the directory to write the filesto
	 */
	public static void generate(int dim, String dirName)
	{
		if(dirName.charAt(dirName.length() - 1) != '/'
			&& dirName.charAt(dirName.length() - 1) != '\\')
			dirName += "/";
		ColorMetadata md = new ColorMetadata(dim);
		java.awt.image.BufferedImage colorImg = new java.awt.image.BufferedImage(md.height,
			md.width, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.image.BufferedImage blackImg = new java.awt.image.BufferedImage(md.height,
			md.width, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.image.BufferedImage bwImg = new java.awt.image.BufferedImage(md.height, md.width,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < md.width; y++)
			for(int x = 0; x < md.height; x++)
			{
				int rgb = md.getRGB(md.width - y, x);
				colorImg.setRGB(x, y, rgb);
				if(rgb != md.background)
					blackImg.setRGB(x, y, 0xFF000000);
				else
					blackImg.setRGB(x, y, rgb);
				if(rgb == md.background)
					bwImg.setRGB(x, y, rgb);
				else
				{
					bwImg.setRGB(x, y, md.getAlphaHex(md.width - y, x, rgb));
				}
			}
		try
		{
			java.io.FileOutputStream out = new java.io.FileOutputStream(dirName
				+ "ColorHexagon.png");
			javax.imageio.ImageIO.write(colorImg, "png", out);
			out.close();

			out = new java.io.FileOutputStream(dirName + "ShadeHexagon.png");
			javax.imageio.ImageIO.write(blackImg, "png", out);
			out.close();

			out = new java.io.FileOutputStream(dirName + "AlphaHexagon.png");
			javax.imageio.ImageIO.write(bwImg, "png", out);
			out.close();
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not write image files to " + dirName, e);
		}
	}
}
