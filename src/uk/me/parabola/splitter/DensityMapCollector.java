package uk.me.parabola.splitter;

/**
 * Builds up a density map.
 */
class DensityMapCollector implements MapCollector {

	private final DensityMap densityMap;
	private final MapDetails details = new MapDetails();

	DensityMapCollector(int resolution) {
		this(null, resolution);
	}

	DensityMapCollector(Area bounds, int resolution) {
		if (bounds == null) {
			// If we don't receive any bounds we have to assume the whole planet
			bounds = new Area(-0x400000, -0x800000, 0x400000, 0x800000);
		}
		densityMap = new DensityMap(bounds, resolution);
	}

	@Override
	public boolean isStartNodeOnly() {
		return true;
	}

	@Override
	public void startNode(int id, double lat, double lon) {
		int glat = Utils.toMapUnit(lat);
		int glon = Utils.toMapUnit(lon);
		densityMap.addNode(glat, glon);
		details.addToBounds(glat, glon);
	}

	@Override
	public void startWay(int id) {}

	@Override
	public void startRelation(int id) {}

	@Override
	public void nodeTag(String key, String value) {}

	@Override
	public void wayTag(String key, String value) {}

	@Override
	public void relationTag(String key, String value) {}

	@Override
	public void wayNode(int nodeId) {}

	@Override
	public void relationNode(int nodeId, String role) {}

	@Override
	public void relationWay(int wayId, String role) {}

	@Override
	public void endNode() {}

	@Override
	public void endWay() {}

	@Override
	public void endRelation() {}

	@Override
	public void endMap() {}

	@Override
	public Area getExactArea() {
		return details.getBounds();
	}

	@Override
	public SplittableArea getRoundedArea(int resolution) {
		Area bounds = RoundingUtils.round(details.getBounds(), resolution);
		return new SplittableDensityArea(densityMap.subset(bounds));
	}}