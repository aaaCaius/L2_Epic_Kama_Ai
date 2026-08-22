package com.l2jfrozen.gameserver.datatables.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.l2jfrozen.Config;
import com.l2jfrozen.gameserver.model.L2NpcWalkerNode;

/**
 * Admin tool use to generate XYZ coordinates  and save in CSV<br>
 * @author Caius RPG for L2Epic Project
 * @author ProGramMoS
 * @since  927
 */


public class PathGenerator
{
	private static PathGenerator instance;
	
	private List<PathGenerator> routes;
	
	public static PathGenerator getInstance()
	{
		if (instance == null)
		{
			instance = new PathGenerator();
		}
		
		return instance;
	}
	
	public void writefile(int x,int y, int z)
	
	{
	    try {
	        FileWriter myWriter = new FileWriter("Config.DATAPACK_ROOT + \"/data/csv/path.txt\"");
	        myWriter.write("Files in Java might be tricky, but it is fun enough!");
	        myWriter.close();
	        System.out.println("Successfully wrote to the file.");
	      } catch (IOException e) {
	        System.out.println("An error occurred.");
	        e.printStackTrace();
	      }
	}
}
