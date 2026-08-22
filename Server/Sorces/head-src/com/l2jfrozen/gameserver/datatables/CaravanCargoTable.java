package com.l2jfrozen.gameserver.datatables;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.l2jfrozen.L2Properties;

/**
 * Holds the cargo carried by each caravan NPC.<BR>
 * <BR>
 * A caravan is a travelling trader: it walks a route defined in walker_routes.csv and, when killed,
 * spills its entire cargo on the ground. Unlike a normal droplist there is no RNG and no rate
 * multiplier - the cargo IS the reward, and it drops in full.<BR>
 * <BR>
 * Configured in <code>config/functions/caravan.properties</code> as:<BR>
 * <code>Cargo100203 = 57,50000;1538,2;3936,5</code><BR>
 * meaning <code>itemId,count</code> pairs separated by <code>;</code>.
 */
public class CaravanCargoTable
{
	private static final Logger LOGGER = Logger.getLogger(CaravanCargoTable.class);
	
	private static final String CONFIG_FILE = "config/functions/caravan.properties";
	
	private static CaravanCargoTable instance;
	
	/** npcId -&gt; cargo manifest */
	private final Map<Integer, List<CargoItem>> cargoByNpc = new HashMap<>();
	
	private boolean enabled = true;
	private int dropRadius = 40;
	
	/** A single item line of a caravan manifest. */
	public static class CargoItem
	{
		private final int itemId;
		private final int count;
		
		public CargoItem(final int itemId, final int count)
		{
			this.itemId = itemId;
			this.count = count;
		}
		
		public int getItemId()
		{
			return itemId;
		}
		
		public int getCount()
		{
			return count;
		}
	}
	
	public static CaravanCargoTable getInstance()
	{
		if (instance == null)
		{
			instance = new CaravanCargoTable();
		}
		
		return instance;
	}
	
	private CaravanCargoTable()
	{
		load();
	}
	
	private void load()
	{
		cargoByNpc.clear();
		
		final File file = new File(CONFIG_FILE);
		
		if (!file.exists())
		{
			LOGGER.warn("CaravanCargoTable: " + CONFIG_FILE + " not found, caravans will drop nothing.");
			return;
		}
		
		try
		{
			final L2Properties settings = new L2Properties();
			
			try (InputStream is = new FileInputStream(file))
			{
				settings.load(is);
			}
			
			enabled = Boolean.parseBoolean(settings.getProperty("CaravanEnabled", "True"));
			dropRadius = Integer.parseInt(settings.getProperty("CaravanCargoDropRadius", "40").trim());
			
			for (final Object rawKey : settings.keySet())
			{
				final String key = String.valueOf(rawKey);
				
				if (!key.startsWith("Cargo"))
				{
					continue;
				}
				
				final int npcId;
				
				try
				{
					npcId = Integer.parseInt(key.substring("Cargo".length()).trim());
				}
				catch (final NumberFormatException e)
				{
					LOGGER.warn("CaravanCargoTable: ignoring malformed key '" + key + "'.");
					continue;
				}
				
				final List<CargoItem> manifest = parseManifest(key, settings.getProperty(key, ""));
				
				if (!manifest.isEmpty())
				{
					cargoByNpc.put(npcId, manifest);
				}
			}
			
			LOGGER.info("CaravanCargoTable: Loaded cargo manifests for " + cargoByNpc.size() + " caravan(s).");
		}
		catch (final Exception e)
		{
			LOGGER.error("CaravanCargoTable: Failed to load " + CONFIG_FILE, e);
		}
	}
	
	private List<CargoItem> parseManifest(final String key, final String value)
	{
		final List<CargoItem> manifest = new ArrayList<>();
		
		if (value == null || value.trim().isEmpty())
		{
			return manifest;
		}
		
		for (final String entry : value.split(";"))
		{
			final String trimmed = entry.trim();
			
			if (trimmed.isEmpty())
			{
				continue;
			}
			
			final String[] pair = trimmed.split(",");
			
			if (pair.length != 2)
			{
				LOGGER.warn("CaravanCargoTable: " + key + " - expected 'itemId,count' but got '" + trimmed + "', skipping.");
				continue;
			}
			
			try
			{
				final int itemId = Integer.parseInt(pair[0].trim());
				final int count = Integer.parseInt(pair[1].trim());
				
				if (itemId <= 0 || count <= 0)
				{
					LOGGER.warn("CaravanCargoTable: " + key + " - itemId and count must be positive, got '" + trimmed + "', skipping.");
					continue;
				}
				
				manifest.add(new CargoItem(itemId, count));
			}
			catch (final NumberFormatException e)
			{
				LOGGER.warn("CaravanCargoTable: " + key + " - non-numeric entry '" + trimmed + "', skipping.");
			}
		}
		
		return manifest;
	}
	
	/**
	 * @param  npcId the caravan template id
	 * @return       its cargo manifest, or an empty list if it carries nothing
	 */
	public List<CargoItem> getCargo(final int npcId)
	{
		final List<CargoItem> manifest = cargoByNpc.get(npcId);
		
		return manifest == null ? new ArrayList<CargoItem>() : manifest;
	}
	
	public boolean isEnabled()
	{
		return enabled;
	}
	
	public int getDropRadius()
	{
		return dropRadius;
	}
	
	public void reload()
	{
		load();
	}
}
