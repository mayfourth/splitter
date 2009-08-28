package uk.me.parabola.splitter;

import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

/**
 * Parses a KML area file.
 *
 * @author Chris Miller
 */
public class KmlParser extends AbstractXppParser {

	private enum State { None, Placemark, Name, Polygon, OuterBoundaryIs, LinearRing, Coordinates }

	private State state = State.None;
	private int currentId;
	private int[] currentCoords = new int[10];
	private List<SubArea> areas = new ArrayList<SubArea>();

	public KmlParser() throws XmlPullParserException {
	}

	public List<SubArea> getAreas() {
		return areas;
	}

	@Override
	protected boolean startElement(String name) throws XmlPullParserException {
		switch (state) {
		case None:
			if (name.equals("Placemark"))
				state = State.Placemark;
			break;
		case Placemark:
			if (name.equals("name")) {
				state = State.Name;
			} else if (name.equals("Polygon")) {
				state = State.Polygon;
			}
			break;
		case Polygon:
			if (name.equals("outerBoundaryIs")) {
				state = State.OuterBoundaryIs;
			}
			break;
		case OuterBoundaryIs:
			if (name.equals("LinearRing")) {
				state = State.LinearRing;
			}
			break;
		case LinearRing:
			if (name.equals("coordinates")) {
				state = State.Coordinates;
			}
			break;
		}
		return false;
	}

	@Override
	protected void text() throws XmlPullParserException {
		if (state == State.Name) {
			String idStr = getTextContent();
			try {
				currentId = Integer.valueOf(idStr);
			} catch (NumberFormatException e) {
				throw createException("Unexpected area name encountered. Expected a valid number, found \"" + idStr + '"');
			}
		} else if (state == State.Coordinates) {
			String coordText = getTextContent();
			String[] coordPairs = coordText.trim().split("\\s+");
			if (coordPairs.length != 5) {
				throw createException("Unexpected number of coordinates. Expected 5, found " + coordPairs.length + " in \"" + coordText + '"');
			}
			for (int i = 0; i < 5; i++) {
				String[] coordStrs = coordPairs[i].split(",");
				if (coordStrs.length != 2) {
					throw createException(
									"Unexpected coordinate pair encountered in \"" + coordPairs[i] + "\". Expected 2 numbers, found " + coordStrs.length);
				}
				for (int j = 0; j < 2; j++) {
					try {
						Double val = Double.valueOf(coordStrs[j]);
						currentCoords[i * 2 + j] = Utils.toMapUnit(val);
					} catch (NumberFormatException e) {
						throw createException("Unexpected coordinate encountered. \"" + coordStrs[j] + "\" is not a valid number");
					}
				}
			}
		}
	}

	@Override
	protected void endElement(String name) throws XmlPullParserException {
		if (state == State.Name) {
			state = State.Placemark;
		} else if (state == State.Coordinates) {
			state = State.LinearRing;
		} else if (name.equals("Placemark")) {
			int minLat = currentCoords[0];
			int minLon = currentCoords[1];
			int maxLat = currentCoords[4];
			int maxLon = currentCoords[5];
			SubArea area = new SubArea(new Area(minLat, minLon, maxLat, maxLon));
			area.setMapid(currentId);
			areas.add(area);
			state = State.None;
		}
	}
}
