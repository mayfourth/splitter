package uk.me.parabola.splitter;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * 
 */
public class TestCustomCollections {
	@Test
	public void testIntList() {
		SplitIntList list = new SplitIntList(10);

		for (int i = -1000; i < 1000; i++) {
			list.add(i);
		}
		Assert.assertEquals(list.size(), 2000);

		for (int i = 0; i < 2000; i++) {
			Assert.assertEquals(list.get(i), i - 1000);
		}

		SplitIntList.Iterator it = list.getIterator();
		int i = 0;
		while (it.hasNext()) {
			Assert.assertEquals(it.next(), i++ - 1000);
		}

		it = list.getDeletingIterator();
		i = 0;
		while (it.hasNext()) {
			Assert.assertEquals(it.next(), i++ - 1000);
		}
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void testInit() {
		new IntObjMap<String>(123, 0.5f);
	}

	@Test
	public void testIntIntMap() {
		testMap(new IntIntMap());
		testMap(new IntIntMap(64, 0.7f));
		testMap(new IntIntMap(1024, 0.7f));
	}

	private void testMap(IntIntMap map) {
		for (int i = 1; i < 1000; i++) {
			int j = map.put(i, i);
			Assert.assertEquals(j, 0);
			Assert.assertEquals(map.size(), i);
		}

		for (int i = 1; i < 1000; i++) {
			Assert.assertEquals(map.get(i), i);
		}

		for (int i = 1000; i < 2000; i++) {
			Assert.assertEquals(map.get(i), 0);
		}

		for (int i = -2000; i < -1000; i++) {
			Assert.assertEquals(map.get(i), 0);
		}

		Assert.assertEquals(map.get(123456), 0);
		map.put(123456, 999);
		Assert.assertEquals(map.get(123456), 999);
		map.put(123456, 888);
		Assert.assertEquals(map.get(123456), 888);
	}

	@Test
	public void testIntObjMap() {
		testMap(new IntObjMap<String>());
		testMap(new IntObjMap<String>(64, 0.7f));
		testMap(new IntObjMap<String>(1024, 0.7f));
	}

	private void testMap(IntObjMap<String> map) {
		for (int i = 1; i < 1000; i++) {
			map.put(i, String.valueOf(i));
			Assert.assertEquals(map.size(), i);
		}

		for (int i = 1; i < 1000; i++) {
			Assert.assertEquals(map.get(i), String.valueOf(i));
		}

		for (int i = 1000; i < 2000; i++) {
			Assert.assertEquals(map.get(i), null);
		}

		for (int i = -2000; i < -1000; i++) {
			Assert.assertEquals(map.get(i), null);
		}
	}
}
