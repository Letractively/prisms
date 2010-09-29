/*
 * RandomAccessFile.java Created Aug 24, 2010 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.IOException;

/**
 * A random access text file allows a text file to be accessed randomly starting at any position. It
 * keeps a buffer of a configurable length, enabling successive access operations to be as efficient
 * as possible.
 */
public class RandomAccessTextFile
{
	class CircularCharBuffer
	{
		private char [] theBuffer;

		private int start;

		private int end;

		private int pos;

		CircularCharBuffer(int length)
		{
			theBuffer = new char [length];
		}

		void resize(int size)
		{
			char [] newBuffer = new char [size];
			boolean rotated = start <= end;
			int newPos = 0;
			for(int i = start; (i < end || !rotated) && newPos < newBuffer.length; i++, newPos++)
			{
				if(i == theBuffer.length)
				{
					i = 0;
					rotated = true;
				}
				int tempPos = i - start;
				if(tempPos < 0)
					tempPos += theBuffer.length;
				newBuffer[tempPos] = theBuffer[i];
			}
			end -= start;
			if(end < 0)
				end += theBuffer.length;
			pos -= start;
			if(pos < 0)
				pos += theBuffer.length;
			start = 0;
			theBuffer = newBuffer;
		}

		void add(char c)
		{
			theBuffer[pos] = c;
			pos++;
			if(pos == theBuffer.length)
				pos = 0;
			end = pos;
			if(start == end)
				start++;
		}

		char get()
		{
			return theBuffer[pos++];
		}

		int getPos()
		{
			int ret = pos - start;
			if(ret < 0)
				ret += theBuffer.length;
			return ret;
		}

		int getRemaining()
		{
			int ret = end - pos;
			if(ret < 0)
				ret += theBuffer.length;
			if(ret >= getLength())
				return 0;
			return ret;
		}

		int getLength()
		{
			int ret = end - start;
			if(ret < 0)
				ret += theBuffer.length;
			return ret;
		}

		void reset()
		{
			start = end = pos = 0;
		}

		void move(int dist)
		{
			int newPos = pos + dist;
			if(dist < 0)
			{
				if(-dist > getPos())
				{
					reset();
					return;
				}
				if(newPos < 0)
					newPos += theBuffer.length;
			}
			else
			{
				if(dist >= getRemaining())
				{
					reset();
					return;
				}
				if(newPos >= theBuffer.length)
					newPos -= theBuffer.length;
			}
			pos = newPos;
		}

		void clear()
		{
			theBuffer = null;
		}
	}

	/**
	 * A reader that reads a file from a given position, keeping a buffer for efficient operation
	 * when moving positions
	 */
	public class RandomAccessReader extends java.io.Reader
	{
		private int thePos;

		private java.io.FileReader theFileReader;

		private int theReaderIndex;

		private CircularCharBuffer theBuffer;

		RandomAccessReader(int length)
		{
			theBuffer = new CircularCharBuffer(length);
		}

		@Override
		public int read(char [] cbuf, int off, int len) throws IOException
		{
			int rem = theBuffer.getRemaining();
			for(int i = 0; i < rem && i < len; i++)
				cbuf[off + i] = theBuffer.get();
			int count = rem < len ? rem : len;
			thePos += count;
			if(rem < len)
			{
				if(theFileReader == null || theReaderIndex != thePos)
					resetReader();
				count += theFileReader.read(cbuf, off + count, len - count);
				theReaderIndex += count - rem;
				thePos += count - rem;
				for(int i = rem + off; i < off + count; i++)
					theBuffer.add(cbuf[i]);
			}
			return count;
		}

		/**
		 * Does nothing. No resources are released here in case of further
		 * {@link RandomAccessTextFile#access} operations. Use
		 * {@link RandomAccessTextFile#close(boolean)} when done with the random access file in
		 * general.
		 */
		@Override
		public void close() throws IOException
		{
		}

		/** Resets this reader's internal reader to start at this reader's current position */
		private void resetReader() throws IOException
		{
			if(theFileReader != null)
				theFileReader.close();
			theFileReader = new java.io.FileReader(theFile);
			theFileReader.skip(thePos);
			theReaderIndex = thePos;
		}

		void move(int pos)
		{
			theBuffer.move(pos - thePos);
			thePos = pos;
		}

		void setBufferLength(int length)
		{
			theBuffer.resize(length);
		}

		void reallyClose() throws IOException
		{
			theBuffer.clear();
			if(theFileReader != null)
				theFileReader.close();
		}
	}

	java.io.File theFile;

	private RandomAccessReader theReader;

	/**
	 * Creates a random access file for a given file
	 * 
	 * @param file The file to access randomly
	 */
	public RandomAccessTextFile(java.io.File file)
	{
		this(file, 10240);
	}

	/**
	 * Creates a random access file for a given file and buffer length
	 * 
	 * @param file The file to access randomly
	 * @param bufferLength The length of the buffer to keep, making random access quicker
	 */
	public RandomAccessTextFile(java.io.File file, int bufferLength)
	{
		theFile = file;
		theReader = new RandomAccessReader(bufferLength);
	}

	/**
	 * @param length The length of the buffer to keep
	 */
	public void setBufferLength(int length)
	{
		theReader.setBufferLength(length);
	}

	/**
	 * @param position The position to start reading this file at
	 * @return A reader that reads the content of this file from the given position
	 * @throws java.io.IOException If an exception occurs reading the file to the given position
	 */
	public RandomAccessReader access(int position) throws java.io.IOException
	{
		theReader.move(position);
		return theReader;
	}

	/**
	 * @return The file that this wrapper accesses
	 */
	public java.io.File getFile()
	{
		return theFile;
	}

	/**
	 * Closes this file, releasing all resources
	 * 
	 * @param deleteFile Whether to delete the text file
	 * @throws IOException If an error occurs releasing the resources
	 */
	public void close(boolean deleteFile) throws IOException
	{
		theReader.reallyClose();
		if(deleteFile)
			theFile.delete();
		theFile = null;
	}
}
