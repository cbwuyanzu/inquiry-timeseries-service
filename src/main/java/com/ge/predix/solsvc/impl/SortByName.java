package com.ge.predix.solsvc.impl;

import java.util.Comparator;

class SortByName implements Comparator {
	 public int compare(Object o1, Object o2) {
	  Pm25Sensor s1 = (Pm25Sensor) o1;
	  Pm25Sensor s2 = (Pm25Sensor) o2;
	  return s1.getPointID().compareTo(s2.getPointID());
	 }
}