package com.l2jfrozen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.log4j.Logger;

public final class L2Properties extends Properties
{
	private static final long serialVersionUID = -4599023842346938325L;
	protected static final Logger LOGGER = Logger.getLogger(L2Properties.class);
	
	public L2Properties()
	{
	}
	
	public L2Properties(String name) throws IOException
	{
		load(new FileInputStream(name));
	}
	
	public L2Properties(File file) throws IOException
	{
		load(new FileInputStream(file));
	}
	
	public L2Properties(InputStream inStream)
	{
		load(inStream);
	}
	
	public L2Properties(Reader reader)
	{
		load(reader);
	}
	
	public void load(String name) throws IOException
	{
		load(new FileInputStream(name));
	}
	
	public void load(File file) throws IOException
	{
		load(new FileInputStream(file));
	}
	
	@Override
	public synchronized void load(InputStream inStream)
	{
		try
		{
			super.load(inStream);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (inStream != null)
			{
				try
				{
					inStream.close();
				}
				catch (final IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public synchronized void load(Reader reader)
	{
		try
		{
			super.load(reader);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (reader != null)
			{
				try
				{
					reader.close();
				}
				catch (final IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public String getProperty(String key)
	{
		String property = super.getProperty(key);
		
		if (property == null)
		{
			LOGGER.error("L2Properties.getProperty(key): Missing property for key - " + key);
			return null;
		}
		return property.trim();
	}
	
	@Override
	public String getProperty(String key, String defaultValue)
	{
		String property = super.getProperty(key, defaultValue);
		
		if (property == null)
		{
			LOGGER.error("L2Properties.getProperty(key,defaultValue): Missing defaultValue for key - " + key);
			return null;
		}
		return property.trim();
	}
}