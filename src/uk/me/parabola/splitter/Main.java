/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 3
 *  as published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Dec 18, 2007
 */
package uk.me.parabola.splitter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.tools.bzip2.CBZip2InputStream;

import org.xmlpull.v1.XmlPullParserException;

/**
 * Splitter for OSM files with the purpose of providing input files for mkgmap.
 *
 * The input file is split so that no piece has more than a given number of nodes in it.
 *
 * @author Steve Ratcliffe
 */
public class Main {

	// We can only process a maximum of 255 areas at a time because we
	// compress an area ID into 8 bits to save memory (and 0 is reserved)
	private int maxAreasPerPass = 255;

	private List<String> filenames = new ArrayList<String>();

	// Traditional default, but please use a different one!
	private int mapid = 63240001;

	// The amount in map units that tiles overlap (note that the final img's will not overlap
	// but the input files do).
	private int overlapAmount = 2000;

	// The max number of nodes that will appear in a single file.
	private int maxNodes = 1600000;

	// The maximum resolution of the map to be produced by mkgmap. This is a value in the range
	// 0-24. Higher numbers mean higher detail. The resolution determines how the tiles must
	// be aligned. Eg a resolution of 13 means the tiles need to have their edges aligned to
	// multiples of 2 ^ (24 - 13) = 2048 map units, and their widths and heights must be a multiple
	// of 2 * 2 ^ (24 - 13) = 4096 units. The tile widths and height multiples are double the tile
	// alignment because the center point of the tile is stored, and that must be aligned the
	// same as the tile edges are.
	private int resolution = 13;

	// Set if there is a previous area file given on the command line.
	private AreaList areaList;
	private boolean mixed;

	public static void main(String[] args) {
		Main m = new Main();

		long start = System.currentTimeMillis();
		System.out.println("Time started: " + new Date());

		try {
			m.split(args);
		} catch (IOException e) {
			System.err.println("Error opening or reading file " + e);
		} catch (XmlPullParserException e) {
			System.err.println("Error parsing xml from file " + e);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}

		System.out.println("Time finished: " + new Date());
		System.out.println("Total time taken: " + (System.currentTimeMillis() - start)/1000 + "s");
	}

	private void split(String[] args) throws IOException, ParserConfigurationException, XmlPullParserException {
		readArgs(args);

		if (areaList == null) {
			int alignment = 1 << (24 - resolution);
			System.out.println("Map is being split for resolution " + resolution + ':');
			System.out.println(" - area boundaries are aligned to 0x" + Integer.toHexString(alignment) + " map units");
			System.out.println(" - areas are multiples of 0x" + Integer.toHexString(alignment * 2) + " map units wide and high");
			areaList = calculateAreas();
		}

		writeAreas(areaList);
		writeArgsFile();
	}

	/**
	 * Deal with the command line arguments.
	 */
	private void readArgs(String[] args) {
		Properties props = new Properties();

		for (String arg : args) {
			if (arg.startsWith("--")) {
				Pattern pattern = Pattern.compile("--(.*)=(.*)");
				Matcher m = pattern.matcher(arg);
				if (m.find()) {
					String key = m.group(1);
					String val = m.group(2);
					System.out.printf("%s = %s\n", key, val);
					props.setProperty(key, val);
				}
			} else {
				filenames.add(arg);
			}
		}

		EnhancedProperties config = new EnhancedProperties(props);

		mapid = config.getProperty("mapid", config.getProperty("mapname", mapid));
		overlapAmount = config.getProperty("overlap", overlapAmount);
		maxNodes = config.getProperty("max-nodes", maxNodes);
		resolution = config.getProperty("resolution", resolution);
		if (resolution < 1 || resolution > 24) {
			System.err.println("The --resolution parameter must be a value between 1 and 24. Resetting to 13.");
			resolution = 13;
		}
		mixed = config.getProperty("mixed", false);
		maxAreasPerPass = config.getProperty("max-areas", maxAreasPerPass);
		if (maxAreasPerPass < 1 || maxAreasPerPass > 255) {
			System.err.println("The --max-areas parameter must be a value between 1 and 255. Resetting to 255.");
			maxAreasPerPass = 255;
		}

		if (config.containsKey("split-file")) {
			String splitFile = config.getProperty("split-file");
			try {
				areaList = new AreaList();
				areaList.read(splitFile);
				areaList.dump();
			} catch (IOException e) {
				areaList = null;
				System.err.println("Could not read area list file " + e);
			}
		}
	}

	/**
	 * Calculate the areas that we are going to split into by getting the total area and
	 * then subdividing down until each area has at most max-nodes nodes in it.
	 */
	private AreaList calculateAreas() throws IOException, XmlPullParserException {
		if (filenames.isEmpty())
			throw new FileNotFoundException("No filename given");

		DivisionParser xmlHandler = new DivisionParser();
		xmlHandler.setMixed(mixed);

		for (String filename : filenames) {
			System.out.println("Processing " + filename);
			Reader reader = openFile(filename);
			xmlHandler.setReader(reader);
			try {
				// First pass, read nodes and split into areas.
				xmlHandler.parse();
			} finally {
				// Release resources
				reader.close();
			}
		}
		System.out.println("A total of " + xmlHandler.getNodeCount() +
						" nodes were processed in " + filenames.size() + (filenames.size() == 1 ? " file" : " files"));
		System.out.println("Min node ID = " + xmlHandler.getMinNodeId());
		System.out.println("Max node ID = " + xmlHandler.getMaxNodeId());
		System.out.println("Time: " + new Date());

		Area exactArea = xmlHandler.getExactArea();
		SubArea totalArea = xmlHandler.getRoundedArea(resolution);
		System.out.println("Exact map coverage is " + exactArea);
		System.out.println("Rounded map coverage is " + totalArea.getBounds());
		System.out.println("Splitting nodes into areas containing a maximum of " + Utils.format(maxNodes) + " nodes each...");
		AreaSplitter splitter = new AreaSplitter(resolution);
		areaList = splitter.split(totalArea, maxNodes);

		// Set the mapid's
		for (SubArea a : areaList.getAreas())
			a.setMapid(mapid++);

		System.out.println(areaList.getAreas().length + " areas generated:");
		for (SubArea a : areaList.getAreas()) {
			System.out.println("Area " + a.getMapid() + " contains " + Utils.format(a.getSize()) + " nodes " + a.getBounds());
		}

		areaList.write("areas.list");

		return areaList;
	}

	/**
	 * Second pass, we have the areas so parse the file(s) again and write out each element
	 * to the file(s) that should contain it.
	 * @param areaList Area list determined on the first pass.
	 */
	private void writeAreas(AreaList areaList) throws IOException, XmlPullParserException {
		System.out.println("Writing out split osm files " + new Date());

		SubArea[] allAreas = areaList.getAreas();

		int passesRequired = (int) Math.ceil((double) allAreas.length / (double) maxAreasPerPass);
		maxAreasPerPass = (int) Math.ceil((double) allAreas.length / (double) passesRequired);

		if (passesRequired > 1) {
			System.out.println("Processing " + allAreas.length + " areas in " + passesRequired + " passes, " + maxAreasPerPass + " areas at a time");
		} else {
			System.out.println("Processing " + allAreas.length + " areas in a single pass");
		}

		for (int i = 0; i < passesRequired; i++) {
			SubArea[] currentAreas = new SubArea[Math.min(maxAreasPerPass, allAreas.length - i * maxAreasPerPass)];
			System.arraycopy(allAreas, i * maxAreasPerPass, currentAreas, 0, currentAreas.length);

			for (SubArea a : currentAreas)
				a.initForWrite(overlapAmount);

			try {
				System.out.println("Starting pass " + (i + 1) + ", processing " + currentAreas.length +
													 " areas (" + currentAreas[0].getMapid() + " to " +
								           currentAreas[currentAreas.length - 1].getMapid() + ')');
				SplitParser xmlHandler = new SplitParser(currentAreas);
				for (String filename : filenames) {
					Reader reader = openFile(filename);
					xmlHandler.setReader(reader);
					try {
						xmlHandler.parse();
					} finally {
						reader.close();
					}
				}
				System.out.println("Wrote " + Utils.format(xmlHandler.getNodeCount()) + " nodes, " +
																			Utils.format(xmlHandler.getWayCount()) + " ways, " +
																			Utils.format(xmlHandler.getRelationCount()) + " relations");
			} finally {
				for (SubArea a : currentAreas)
					a.finishWrite();
			}
		}
	}

	/**
	 * Write a file that can be given to mkgmap that contains the correct arguments
	 * for the split file pieces.  You are encouraged to edit the file and so it
	 * contains a template of all the arguments that you might want to use.
	 */
	protected void writeArgsFile() {
		PrintWriter w;
		try {
			w = new PrintWriter(new FileWriter("template.args"));
		} catch (IOException e) {
			System.err.println("Could not write template.args file");
			return;
		}

		w.println("#");
		w.println("# This file can be given to mkgmap using the -c option");
		w.println("# Please edit it first to add a description of each map.");
		w.println("#");
		w.println();

		w.println("# You can set the family id for the map");
		w.println("# family-id: 980");
		w.println("# product-id: 1");

		w.println();
		w.println("# Following is a list of map tiles.  Add a suitable description");
		w.println("# for each one.");
		for (SubArea a : areaList.getAreas()) {
			w.println();
			w.format("mapname: %d\n", a.getMapid());
			w.println("description: OSM Map");
			w.format("input-file: %d.osm.gz\n", a.getMapid());
		}

		w.println();
		w.close();
	}

	/**
	 * Open a file and apply filters necessary to reading it such as decompression.
	 *
	 * @param name The file to open.
	 * @return A stream that will read the file, positioned at the beginning.
	 * @throws FileNotFoundException If the file cannot be opened for any reason.
	 */
	private Reader openFile(String name) throws IOException {
		InputStream is = new FileInputStream(name);
		if (name.endsWith(".gz")) {
			try {
				is = new GZIPInputStream(is);
			} catch (IOException e) {
				throw new IOException( "Could not read " + name + " as a gz compressed file", e);
			}
		}
		if (name.endsWith(".bz2")) {
			try {
				is.read(); is.read(); is = new CBZip2InputStream(is);
			} catch (IOException e) {
				throw new IOException( "Could not read " + name + " as a bz2 compressed file", e);
			}
		}
		return new InputStreamReader(is, Charset.forName("UTF-8"));
	}
}
