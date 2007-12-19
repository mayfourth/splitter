/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Dec 19, 2007
 */
package uk.me.parabola.splitter;

/**
 * @author Steve Ratcliffe
 */
public class OsmNode {
	private long id;
	private float lat;
	private float lon;

	public OsmNode(String sid, String slat, String slon) {
		id = Long.parseLong(sid);
		lat = Float.parseFloat(slat);
		lon = Float.parseFloat(slon);
	}

	public void addTag(String key, String val) {
	}

	public long getId() {
		return id;
	}
}
